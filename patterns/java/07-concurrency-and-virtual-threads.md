# 07 — Concurrency & Virtual Threads

> **One-liner:** a backend serves many requests at once, so **shared mutable state is the
> enemy**. Make state immutable or confined. When it must be shared, make each **compound
> action atomic** (atomics, concurrent collections, locks, or the database). Use **virtual
> threads** to run huge numbers of blocking tasks cheaply, but still limit what a downstream
> system (like the DB pool) can take.

**Roadmap:** task 1.7 · **Last updated:** 2026-10-02 · **Prev:** [06 — OOP](06-oop-composition-over-inheritance.md)
**Code:** [`lab/.../java/concurrency/`](../lab/src/main/java/com/masternova/java/concurrency/): `Counters`, `Cohort`, `WebhookProcessor`, `QuoteService`, `VirtualThreads`, `RequestContext`, `OutboxRelay`, `StopFlag`
**Tests:** [`lab/.../java/concurrency/`](../lab/src/test/java/com/masternova/java/concurrency/) (with a `Race` start-gate helper) · **Run:** `cd patterns/lab && ./mvnw test -Dtest='com.masternova.java.concurrency.*Test'`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know. In code, `// ⭐` marks the lines to remember.

| # | Section | Priority |
|---|---|---|
| 1 | [Why a backend developer must care](#1-why-a-backend-developer-must-care-) | ⭐⭐⭐ |
| 2 | [The three problems: atomicity, visibility, ordering](#2-the-three-problems-atomicity-visibility-ordering-) | ⭐⭐⭐ |
| 3 | [The toolbox, cheapest first](#3-the-toolbox-cheapest-first-) | ⭐⭐⭐ |
| 4 | [Lost updates: five counters](#4-lost-updates-five-counters-) | ⭐⭐⭐ |
| 5 | [Check-then-act and CAS](#5-check-then-act-and-cas-) | ⭐⭐⭐ |
| 6 | [Idempotency under concurrency (the webhook proof)](#6-idempotency-under-concurrency-the-webhook-proof-) | ⭐⭐⭐ |
| 7 | [Executors and thread pools](#7-executors-and-thread-pools-) | ⭐⭐ |
| 8 | [`CompletableFuture`: fan-out, timeouts, fallbacks](#8-completablefuture-fan-out-timeouts-fallbacks-) | ⭐⭐ |
| 9 | [Virtual threads](#9-virtual-threads-) | ⭐⭐⭐ |
| 10 | [`ScopedValue`, `ThreadLocal`, structured concurrency](#10-scopedvalue-threadlocal-structured-concurrency-) | ⭐⭐ |
| 11 | [Producer–consumer and back-pressure](#11-producerconsumer-and-back-pressure-) | ⭐⭐ |
| 12 | [`volatile` and the Java Memory Model](#12-volatile-and-the-java-memory-model-) | ⭐⭐⭐ |
| 13 | [Deadlock, livelock, starvation](#13-deadlock-livelock-starvation-) | ⭐⭐⭐ |
| 14 | [Concurrency in Spring / Masternova](#14-concurrency-in-spring--masternova-) | ⭐⭐⭐ |
| 15 | [Common mistakes](#15-common-mistakes-) | ⭐⭐⭐ |
| 16 | [Interview Q&A](#16-interview-qa-) | ⭐⭐⭐ |
| 17 | [30-second recall](#17-30-second-recall) | ⭐⭐⭐ |

---

## 1. Why a backend developer must care ⭐⭐⭐

- **Every HTTP request runs on its own thread** (a virtual thread in our app:
  `spring.threads.virtual.enabled=true`). Two learners clicking "Enroll" at the same moment
  means **two threads running the same code at once**.
- **Spring beans are singletons by default:** *one* `CheckoutService` object shared by every
  request thread. **Any mutable field in a bean is shared state.** ⭐⭐⭐ Rule: beans hold only
  `final` references to other beans and immutable config. Request data lives in local
  variables and parameters.
- **Production runs several instances** (2+ pods). In-JVM locks protect nothing *across*
  instances. For cross-instance correctness, the **database** (constraints, row locks,
  optimistic versions) is the referee (§6, §14).

> **Coming from Node/NestJS:** JavaScript runs your code on **one** thread (the event loop),
> so `count++` can't race inside one process. In Java it can, so this whole topic is new
> territory.

---

## 2. The three problems: atomicity, visibility, ordering ⭐⭐⭐

| Problem | What goes wrong | Example here | Fix |
|---|---|---|---|
| **Atomicity** | a multi-step action is interleaved with another thread's | `count++` is read → add → write, so updates get lost (`UnsafeCounter`). Check-then-act oversells (`tryEnrollUnsafe`). | locks, atomics, atomic collection methods |
| **Visibility** | one thread's write is never *seen* by another (CPU caches, JIT hoisting a read out of a loop) | a worker spinning on a non-volatile `running` flag may never stop (`StopFlag`) | `volatile`, locks, atomics, or concurrent collections (they all create *happens-before*) |
| **Ordering** | the compiler or CPU reorders instructions, so other threads see half-built state | double-checked locking without `volatile` | the same tools; final fields of immutable objects are safe |

A **race condition** is when the result depends on the timing of threads. It's the general
name for the first problem, and the hardest bugs to reproduce, which is why the tests use a
**start gate** (§4).

---

## 3. The toolbox, cheapest first ⭐⭐⭐

| Level | Tool | When |
|---|---|---|
| 1 | **Immutability** (records, `List.copyOf`, note 01) | always first: nothing to synchronise |
| 2 | **Confinement**: data owned by one thread (locals, per-request objects) | the default for request data |
| 3 | **Atomics**: `AtomicInteger`/`AtomicLong`/`AtomicReference`, `LongAdder` | a single shared variable |
| 4 | **Concurrent collections**: `ConcurrentHashMap`, `ConcurrentHashMap.newKeySet()`, `CopyOnWriteArrayList`, `BlockingQueue` | shared maps, sets, queues |
| 5 | **Locks**: `synchronized`, `ReentrantLock`, `ReadWriteLock`, `Semaphore` | several variables that must change together |
| 6 | **The database**: unique constraints, `SELECT … FOR UPDATE [SKIP LOCKED]`, `@Version` | anything that must hold **across instances** |

Higher-level tools (executors, `CompletableFuture`, queues) are built from these.

---

## 4. Lost updates: five counters ⭐⭐⭐

```java
count++;   // ❌ three steps: read 41 · add 1 · write 42. Two threads both read 41 → one increment lost.
```

| Counter | How | Notes |
|---|---|---|
| `UnsafeCounter` | plain `long` | ❌ `theUnsafeCounterLosesUpdates`: 8 threads × 100,000 increments lose thousands |
| `SynchronizedCounter` | `synchronized` methods | ✅ simple. **Reads must synchronise too**, or they may see stale values. |
| `AtomicCounter` | `AtomicLong.incrementAndGet()` | ✅ lock-free (CPU compare-and-swap). The default for one number. |
| `AdderCounter` | `LongAdder` | ✅ fastest under heavy write contention (spreads over cells); reading = `sum()`. Metrics, hit counters. |
| `LockCounter` | `ReentrantLock` + `try/finally unlock` | ✅ like `synchronized`, plus `tryLock(timeout)`, fairness, conditions |

**How the tests make races reproducible:** `Race.run(threads, action)` starts N **platform**
threads that all wait on a `CountDownLatch`, then releases them at once for maximum
contention. Safe counters must hit exactly `8 × 100,000`. The unsafe one is retried for up to
20 rounds until it loses an update (on a multi-core machine, round 1 already does).

---

## 5. Check-then-act and CAS ⭐⭐⭐

Even with thread-safe pieces, a **compound action** can race:

```java
if (takenUnsafe < capacity) {   // check  ← thread A and B both see 9 < 10
  takenUnsafe++;                // act    ← both take the last seat: 11 learners, 10 seats
}
```

`checkThenActOversells` admits more learners than seats. The same bug hides in:

```java
if (!map.containsKey(k)) map.put(k, v);     // ❌ even on a ConcurrentHashMap: two separate calls
map.putIfAbsent(k, v);                      // ✅ one atomic call
map.computeIfAbsent(k, key -> load(key));   // ✅ atomic per key
map.merge(k, 1, Integer::sum);              // ✅ atomic counter per key
```

**CAS (compare-and-swap), the idea behind every atomic class** (`Cohort.tryEnroll`):

```java
while (true) {
  int current = taken.get();
  if (current >= capacity) return false;
  if (taken.compareAndSet(current, current + 1)) return true;   // ⭐ "if still `current`, set next"
  // another thread changed it first → loop and re-check
}
```

The `compareAndSet` either wins atomically or tells you to retry, with no lock. (In real code,
`taken.updateAndGet(...)` hides the loop.) `casLoopNeverOversellsTheLastSeats` lets 200 threads
fight for 100 seats and gets **exactly 100**.

---

## 6. Idempotency under concurrency (the webhook proof) ⭐⭐⭐

Payment providers **retry** webhooks, and can deliver the same event several times at once.
Each event must cause **exactly one** enrollment:

```java
private final Set<String> processed = ConcurrentHashMap.newKeySet();

public boolean handle(String eventId) {
  if (!processed.add(eventId)) return false;   // ⭐ claim first, atomically. Losers stop here.
  enrollments.incrementAndGet();               // the side effect, once
  return true;
}
```

`fiftyConcurrentCopiesOfOneWebhookEnrollExactlyOnce` is the **Phase 9 proof test** in miniature:
50 threads deliver the same event, and the result is 1 winner and 1 enrollment.

⚠️ **In production, this set must be in the database, not in memory.** Two pods each have their
own `Set`, so both would process the event. Phase 9 uses a table with a **unique constraint on
the provider's event id**: `INSERT` the id first ("claim"), and the second insert fails. Same
idea, enforced by Postgres across every instance (ADR-0021 in the NestJS project).

---

## 7. Executors and thread pools ⭐⭐

Don't create threads by hand for each task. Submit tasks to an **`ExecutorService`**:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {   // Java 19+: AutoCloseable
  Future<Money> f = executor.submit(() -> fetchPrice());             // Callable → Future
  Money price = f.get();                                             // blocks; checked exceptions
}                                                                    // close() waits for all tasks
```

| Factory | Threads | ⚠️ |
|---|---|---|
| `newVirtualThreadPerTaskExecutor()` | one **virtual** thread per task | ✅ default for blocking I/O work |
| `newFixedThreadPool(n)` | n platform threads | the queue is **unbounded**, so a backlog can exhaust memory |
| `newCachedThreadPool()` | grows without limit | can create thousands of OS threads |
| `newScheduledThreadPool(n)` | for delayed or periodic tasks | |
| `new ThreadPoolExecutor(…)` | full control: core/max size, **bounded queue**, rejection policy | what you configure for CPU-bound pools |

**Sizing platform pools:** CPU-bound work wants about the number of cores. I/O-bound work used
to need big pools; **that's exactly what virtual threads replace** (§9).

**Shutdown:** `close()` (or `shutdown()` + `awaitTermination`) waits. `shutdownNow()`
interrupts. Leaked non-daemon threads keep the JVM alive.

---

## 8. `CompletableFuture`: fan-out, timeouts, fallbacks ⭐⭐

`QuoteService.cheapest` asks three providers **at the same time**:

```java
CompletableFuture.supplyAsync(p.call(), executor)             // ⭐ run on OUR executor
    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)     // ⭐ never wait forever
    .exceptionally(error -> null);                            // a failure or timeout → no quote

CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();   // wait for all
```

Total time ≈ the **slowest** call, not the sum (`callsRunInParallelSoTotalTimeIsTheSlowestNotTheSum`:
3 × 300 ms finish in under 800 ms).

| Method | Does |
|---|---|
| `supplyAsync(supplier, executor)` / `runAsync` | start async work |
| `thenApply(fn)` | transform the result (like `map`) |
| `thenCompose(fn)` | chain another async step (like `flatMap`) |
| `thenCombine(other, fn)` | combine two independent results |
| `allOf(…)` / `anyOf(…)` | wait for all / the first |
| `exceptionally(fn)` / `handle((r, e) -> …)` | recover from failure |
| `orTimeout(d)` / `completeOnTimeout(value, d)` | fail / default after a deadline |
| `join()` vs `get()` | `join` throws an unchecked `CompletionException`; `get` throws checked `ExecutionException`/`InterruptedException` |

⚠️ **Always pass an executor.** Without one, async work runs on `ForkJoinPool.commonPool()`,
shared by the whole JVM (parallel streams included) and sized for CPU work. Blocking calls there
starve everything else.

⚠️ **`orTimeout` doesn't stop the underlying task.** The future completes exceptionally, but the
slow call keeps running. Give real clients (HTTP, JDBC) their own timeouts too.

---

## 9. Virtual threads ⭐⭐⭐

| | **Platform thread** | **Virtual thread** (Java 21) |
|---|---|---|
| Backed by | one OS thread (~1 MB stack, kernel scheduled) | a small heap object, scheduled by the JVM |
| Cost | expensive: pools of hundreds | cheap: **millions** possible |
| When it blocks (sleep, socket, JDBC) | the OS thread sits idle | it **unmounts**; its **carrier** (an OS thread) runs another virtual thread |
| Pool it? | yes | **no**: one per task (`newVirtualThreadPerTaskExecutor`) |

`tenThousandBlockingTasksOnAHandfulOfCarrierThreads`: 10,000 tasks each block 200 ms
(**2,000 s** of blocking in total), finishing in a few seconds on **≈ CPU-count carrier
threads**. The model is M:N: many virtual threads on few OS threads.

**What they're for:** **I/O-bound** concurrency, the shape of a web backend (wait on DB, wait on
HTTP, wait on Redis). Write plain blocking code (no reactive callbacks) and get async-level
scalability. That's why the skeleton sets `spring.threads.virtual.enabled=true`: every request
gets a virtual thread.

**What they're NOT for:**

- **CPU-bound** work (transcoding, hashing). There are only as many carriers as cores, so
  virtual threads add nothing. Use a bounded platform pool, or a separate deployable. That's why
  ffmpeg lives in the **worker**.
- **Unlimited downstream load.** ⭐⭐⭐ 10,000 virtual threads can all try to use **10** database
  connections (Hikari's default pool). They'll queue on the pool, or time out. Virtual threads
  remove the *thread* limit, not the *resource* limit. Limit with the pool size, or a `Semaphore`
  for an external API's rate limit.

**Pinning:** a virtual thread that can't unmount blocks its carrier. Since **Java 24 (JEP
491)**, `synchronized` no longer pins. Native code (JNI) and some old libraries still can.

**`ThreadLocal` caution:** it works, but with millions of short-lived virtual threads, expensive
per-thread caches (e.g. a `SimpleDateFormat` per thread) are wasted. Prefer immutable shared
objects, or `ScopedValue` (§10).

---

## 10. `ScopedValue`, `ThreadLocal`, structured concurrency ⭐⭐

**The problem:** "who is the current user / tenant / trace id?" is needed deep in the call stack
without passing it through every method.

```java
private static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

ScopedValue.where(CURRENT_USER, "asha").call(() -> service.checkout());   // bound for this call only
CURRENT_USER.orElse("anonymous");                                          // read anywhere inside
```

| | `ThreadLocal` | `ScopedValue` (final in **Java 25**) |
|---|---|---|
| Lifetime | until you `remove()` it (forgetting it leaks across pooled requests ⚠️) | exactly the scope, then gone automatically |
| Mutable? | `set()` anywhere | immutable while bound; nesting rebinds and restores (`scopesNestAndRestore`) |
| Cost with millions of virtual threads | a map entry per thread | cheap, and shared with child tasks |

Spring Security's `SecurityContextHolder` uses a `ThreadLocal` today. It's the same problem.

**Structured concurrency** (`StructuredTaskScope`): fork subtasks inside a scope that ends only
when all of them finish, and cancels the rest if one fails. That's `CompletableFuture` fan-out
with automatic cleanup. In **Java 25 it is still a preview API**:
`error: StructuredTaskScope is a preview API and is disabled by default.` We don't enable preview
features in production code. Know the concept, and use `CompletableFuture` until it's final.

---

## 11. Producer–consumer and back-pressure ⭐⭐

`OutboxRelay.relay`: one producer reads outbox events, and N workers deliver them through a
**bounded** `BlockingQueue`:

```java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(8);   // ⭐ bounded
queue.put(event);     // blocks when full  → BACK-PRESSURE: a fast producer can't drown slow consumers
queue.take();         // blocks when empty
queue.put(POISON_PILL);   // one per worker: "no more work", for a clean shutdown
```

`everyEventIsDeliveredExactlyOnceByTheWorkerPool` checks that 100 events are delivered, with no
duplicates and none lost. An **unbounded** queue would hide the problem until memory runs out.
In production the outbox *table* is the queue, and workers claim rows with
`SELECT … FOR UPDATE SKIP LOCKED` (Phase 2).

---

## 12. `volatile` and the Java Memory Model ⭐⭐⭐

Without synchronisation, a thread may **never see** another thread's write:

```java
private volatile boolean running = true;   // ⭐ remove volatile → the loop may spin forever
while (running) { iterations++; }          // the JIT can hoist a non-volatile read out of the loop
```

`aVolatileFlagIsSeenByTheSpinningThread` checks that the worker exits after `stop()`.

**Happens-before:** the JMM guarantees that a write is visible to a read if a *happens-before*
edge connects them:

- unlocking a lock → a later lock of the same lock;
- a `volatile` write → a later read of that variable;
- `Thread.start()` → anything in the started thread; a thread's actions → `join()` returning;
- a constructor finishing → readers of **`final` fields** (immutable objects are safe to share);
- writes into a concurrent collection → reads that see them.

⚠️ **`volatile` gives visibility, NOT atomicity.** `volatile long count; count++` still loses
updates (read-add-write is three steps). Use it for flags and published references; use atomics
or locks for read-modify-write.

---

## 13. Deadlock, livelock, starvation ⭐⭐⭐

**Deadlock:** thread A holds lock 1 and waits for lock 2, while thread B holds lock 2 and waits
for lock 1. Both wait forever. It needs all four **Coffman conditions**:

1. mutual exclusion;
2. hold-and-wait;
3. no preemption;
4. circular wait.

**Prevention:**

- **Lock ordering:** always acquire locks in a global order (e.g. by account id). Breaks the
  circular wait.
- **`tryLock(timeout)`:** give up and retry instead of waiting forever.
- **Hold locks briefly.** Never call out to unknown code, or do I/O, while holding a lock.
- In the database: the same rules apply to row locks (Postgres detects deadlocks and aborts one
  transaction, and you retry).

**Diagnose:** take a thread dump with `jstack <pid>` (or `jcmd <pid> Thread.print`). The JVM
reports "Found one Java-level deadlock" with both stacks.

| Other liveness failure | Meaning |
|---|---|
| **Livelock** | threads keep reacting to each other and make no progress (both retry in lockstep). Fix with random backoff. |
| **Starvation** | a thread never gets the resource (an unfair lock, a priority problem) |

---

## 14. Concurrency in Spring / Masternova ⭐⭐⭐

| Situation | Rule |
|---|---|
| **Singleton beans** | stateless: only `final` dependencies and config. No mutable fields for request data. |
| **Request data** | method parameters and locals (confined to the request's thread) |
| **Who is the user** | `SecurityContextHolder` (a ThreadLocal) / `ScopedValue` style (§10) |
| **Counting, metrics** | Micrometer counters (thread-safe), `LongAdder` |
| **In-memory caches** | `ConcurrentHashMap.computeIfAbsent`, or Caffeine; Redis across instances (Phase 8) |
| **Exactly-once effects** | **DB unique constraints** + claim-before-process (Phase 9), proven with a 50× concurrent IT |
| **Concurrent edits** | optimistic locking with `@Version` → 409 (Phase 6) |
| **Workers claiming jobs** | `SELECT … FOR UPDATE SKIP LOCKED` (Phase 2 outbox relay) |
| **`@Scheduled` jobs** | single-threaded by default, and **run on every instance**, so use **ShedLock** for "once per cluster" (Phase 7) |
| **`@Async`** | runs on a task executor (virtual threads when enabled); don't fire-and-forget important work, use the outbox |
| **Fan-out to external APIs** | `CompletableFuture` on a virtual-thread executor, with timeouts (`QuoteService`) |
| **CPU-heavy work** | the separate worker deployable with bounded pools (Phase 7 transcoding) |
| **Load** | the DB pool size bounds concurrency; size it consciously (D5 load tests) |

---

## 15. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | § |
|---|---|---|
| a mutable field in a singleton bean | locals, or an immutable / concurrent structure | 1 |
| `count++` on a shared field | `AtomicLong` / `LongAdder` / a lock | 4 |
| synchronising writes but not reads | synchronise both (visibility) | 4 |
| `if (!map.containsKey(k)) map.put(k, v)` | `putIfAbsent` / `computeIfAbsent` | 5 |
| an in-memory "processed" set for webhooks in a multi-pod app | a DB unique constraint | 6 |
| `lock.lock(); work(); lock.unlock();` | unlock in `finally` | 4 |
| `newFixedThreadPool` with unbounded work | a bounded queue + rejection policy, or virtual threads | 7 |
| `supplyAsync(task)` without an executor, for blocking work | pass a virtual-thread executor | 8 |
| no timeout on remote calls | `orTimeout` + client timeouts | 8 |
| pooling virtual threads | one per task | 9 |
| assuming virtual threads remove the DB bottleneck | size pools; use a `Semaphore` for external limits | 9 |
| CPU-heavy work on virtual threads | a bounded platform pool / a separate service | 9 |
| a `ThreadLocal` never `remove()`d | `try/finally remove()`, or `ScopedValue` | 10 |
| an unbounded queue between producer and consumer | a bounded `BlockingQueue` (back-pressure) | 11 |
| `volatile` counter | atomic | 12 |
| taking locks in different orders | a global lock order, `tryLock` | 13 |
| `Thread.sleep` in tests to "wait for" another thread | latches, `join`, `awaitTermination` | 4 |

---

## 16. Interview Q&A ⭐⭐⭐

**Q1. What is a race condition? Give one.**
A result that depends on thread timing. Example: `count++` from two threads loses updates, or
check-then-act oversells seats.

**Q2. `synchronized` vs `ReentrantLock` vs atomics?**
`synchronized` is the simple monitor lock. `ReentrantLock` adds `tryLock` with timeout,
fairness and conditions, and must be unlocked in `finally`. Atomics are lock-free CAS for single
variables; `LongAdder` handles heavy contention.

**Q3. What does `volatile` guarantee? Is `volatile int x; x++` safe?**
Visibility and ordering (a happens-before from write to read), not atomicity. `x++` is still
three steps, so it isn't safe.

**Q4. What is CAS?**
An atomic CPU instruction: "if the value is still X, set it to Y". It underpins the atomic
classes. Retry in a loop on failure. Beware ABA, solvable with stamped/versioned references.

**Q5. `HashMap` vs `ConcurrentHashMap` vs `Collections.synchronizedMap`?**
`HashMap` isn't thread-safe and can be corrupted. `synchronizedMap` puts one lock around
everything. `ConcurrentHashMap` uses fine-grained, mostly lock-free operations, offers atomic
`computeIfAbsent`/`merge`, and allows no nulls.

**Q6. What are virtual threads? When would you not use them?**
JVM-scheduled lightweight threads that unmount while blocked, so millions are possible. They're
great for I/O-bound work, and not useful for CPU-bound work. They don't remove resource limits
(the DB pool). Don't pool them.

**Q7. How do you make a webhook handler idempotent across multiple instances?**
Store the provider event id with a unique constraint: claim it with an insert before processing.
A duplicate insert means the event was already handled, so return 200 and do nothing. Prove it
with a concurrent test.

**Q8. `CompletableFuture`: `thenApply` vs `thenCompose`? Why pass an executor?**
`thenApply` is a synchronous transform (map). `thenCompose` chains an async step (flatMap). Pass
an executor because the default common pool is shared and CPU-sized, and blocking calls on it
starve the JVM.

**Q9. What is a deadlock, and how do you prevent one?**
Circular waiting on locks. Prevent it with a global lock order, `tryLock` with a timeout, short
critical sections, and no I/O while holding a lock. Diagnose with a thread dump.

**Q10. `ThreadLocal` vs `ScopedValue`?**
`ThreadLocal` is mutable and lives until it's removed (leak risk with pools). `ScopedValue` is
immutable, bound for a scope, cleans up automatically, and is cheap with virtual threads. It's
final in Java 25.

**Q11. Is a Spring singleton bean thread-safe?**
Only if it's stateless or its shared state is thread-safe. Spring doesn't make it safe for you.

---

## 17. 30-second recall

- **Shared state:** concurrent requests plus singleton beans mean shared mutable state is the
  enemy. Beans are stateless; request data stays local.
- **Three problems:** atomicity (`count++`, check-then-act), visibility (`volatile`), ordering.
- **Toolbox, cheapest first:** immutability → confinement → atomics → concurrent collections
  (`putIfAbsent`/`computeIfAbsent`/`merge`) → locks (unlock in `finally`) → the database.
- **CAS:** "if still X, set Y", retried in a loop. It's how atomics work.
- **Exactly-once:** claim the event id first. In memory that's `Set.add`; in production it's a
  DB unique constraint. Prove it with a 50× concurrent test.
- **Executors:**
  - One virtual thread per task.
  - `CompletableFuture` with **your** executor, a timeout and a fallback.
  - `orTimeout` doesn't cancel the task.
- **Virtual threads:** cheap blocking, ideal for I/O. Useless for CPU work. They don't lift
  the DB-pool limit. Never pool them.
- **Request context:** `ScopedValue` (final in Java 25) beats `ThreadLocal`. Structured
  concurrency is still preview.
- **Producer–consumer:** a bounded `BlockingQueue` gives back-pressure; a poison pill shuts
  workers down.
- **Deadlock:** a circular wait. Fix with a lock order or `tryLock`; diagnose with `jstack`.
- **Next:** [08 — Spring IoC & DI](README.md) (task 1.8).
