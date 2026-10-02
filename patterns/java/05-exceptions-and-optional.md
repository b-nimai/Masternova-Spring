# 05 — Exceptions & `Optional`

> **One-liner:** use **exceptions** for things that shouldn't happen (bugs, broken
> infrastructure, rule violations the caller must not ignore). Use **return values** (`Optional`,
> `Result`, a report) for expected outcomes. Keep a **stable error code**, keep the **cause**, and
> close resources with **try-with-resources**.

**Roadmap:** task 1.5 · **Last updated:** 2026-10-02 · **Prev:** [04 — Generics](04-generics.md)
**Code:** [`lab/.../java/exceptions/`](../lab/src/main/java/com/masternova/java/exceptions/): `MasternovaException` (+ 4 subclasses) + `ProblemMapper`, `CourseImporter`, `TracingResource`, `FinallyBehaviour`, `CourseLookup`, `Retry` + `ThrowingSupplier`
**Tests:** [`lab/.../java/exceptions/`](../lab/src/test/java/com/masternova/java/exceptions/) · **Run:** `cd patterns/lab && ./mvnw test -Dtest='com.masternova.java.exceptions.*Test'`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know. In code, `// ⭐` marks the lines to remember.

| # | Section | Priority |
|---|---|---|
| 1 | [The hierarchy](#1-the-hierarchy-) | ⭐⭐⭐ |
| 2 | [Checked vs unchecked](#2-checked-vs-unchecked-) | ⭐⭐⭐ |
| 3 | [`try` / `catch` / `finally`: exact behaviour](#3-try--catch--finally-exact-behaviour-) | ⭐⭐⭐ |
| 4 | [try-with-resources and suppressed exceptions](#4-try-with-resources-and-suppressed-exceptions-) | ⭐⭐⭐ |
| 5 | [Designing your own exceptions](#5-designing-your-own-exceptions-) | ⭐⭐⭐ |
| 6 | [Exceptions vs return values](#6-exceptions-vs-return-values-) | ⭐⭐⭐ |
| 7 | [Checked exceptions and lambdas](#7-checked-exceptions-and-lambdas-) | ⭐⭐ |
| 8 | [`InterruptedException`](#8-interruptedexception-) | ⭐⭐ |
| 9 | [`Optional` done right](#9-optional-done-right-) | ⭐⭐⭐ |
| 10 | [Walkthrough of the code](#10-walkthrough-of-the-code-) | ⭐⭐ |
| 11 | [Exceptions in Spring / Masternova](#11-exceptions-in-spring--masternova-) | ⭐⭐⭐ |
| 12 | [Anti-patterns](#12-anti-patterns-) | ⭐⭐⭐ |
| 13 | [Interview Q&A](#13-interview-qa-) | ⭐⭐⭐ |
| 14 | [Play with it in `jshell`](#14-play-with-it-in-jshell-) | ⭐ |
| 15 | [30-second recall](#15-30-second-recall) | ⭐⭐⭐ |

---

## 1. The hierarchy ⭐⭐⭐

```text
Throwable
 ├── Error                      JVM-level trouble: OutOfMemoryError, StackOverflowError.
 │                              Don't catch these (you usually can't recover).
 └── Exception                  ── CHECKED: the compiler forces callers to handle or declare it
      │                            IOException, SQLException, InterruptedException, TimeoutException
      └── RuntimeException      ── UNCHECKED: not enforced by the compiler
           IllegalArgumentException (incl. NumberFormatException), IllegalStateException,
           NullPointerException, UnsupportedOperationException, ArithmeticException,
           ClassCastException, IndexOutOfBoundsException, ConcurrentModificationException …
           and all of Masternova's domain exceptions (MasternovaException)
```

**The JDK exceptions you should throw yourself:**

| Situation | Throw |
|---|---|
| a bad argument | `IllegalArgumentException` |
| a null argument | `NullPointerException` (via `Objects.requireNonNull`) |
| an object in the wrong state for this call ("already closed", "not started") | `IllegalStateException` |
| an operation the object doesn't support | `UnsupportedOperationException` |
| an index out of range | `IndexOutOfBoundsException` (via `Objects.checkIndex`) |

---

## 2. Checked vs unchecked ⭐⭐⭐

| | **Checked** (`extends Exception`) | **Unchecked** (`extends RuntimeException`) |
|---|---|---|
| Compiler | forces `catch` or `throws` at every caller | no enforcement |
| Meant for | recoverable conditions outside your control: a missing file, a network failure | programming errors and broken invariants: bad argument, illegal state |
| Examples | `IOException`, `SQLException` | `IllegalArgumentException`, `NullPointerException` |
| Real error when ignored | `unreported exception IOException; must be caught or declared to be thrown` | none |

**What modern Java and Spring actually do:** checked exceptions turned out painful at scale.
They leak through layers, they don't work with lambdas (§7), and callers often can't recover
anyway. So:

- **Spring converts** checked infrastructure exceptions into unchecked ones. JDBC's
  `SQLException` becomes Spring's `DataAccessException` hierarchy.
- **Your domain exceptions are unchecked.** `MasternovaException extends RuntimeException`.
- **⭐⭐⭐ Spring trap:** `@Transactional` **rolls back on unchecked exceptions (and `Error`) by
  default, NOT on checked ones.** Throw a checked exception from a transactional method and
  the transaction **commits**. Either use unchecked exceptions, or declare
  `@Transactional(rollbackFor = Exception.class)`.
- **Still use checked** at a genuine boundary where the caller *can* react: `CourseImporter`
  declares `throws IOException`, and the caller decides whether to retry, skip, or report.

---

## 3. `try` / `catch` / `finally`: exact behaviour ⭐⭐⭐

```java
try {
  risky();
} catch (NoSuchFileException e) {        // most specific FIRST
  …
} catch (IOException | SQLException e) { // multi-catch (Java 7): one handler, several types
  …
} finally {
  cleanup();                             // runs on success, exception, AND return
}
```

| Rule | Detail |
|---|---|
| **Specific before general** | `catch (Exception e)` then `catch (IOException e)` → `error: exception IOException has already been caught` |
| `finally` always runs | even when `try` **returns**: the return value is computed first, then `finally` runs (`finallyRunsAfterTheReturnValueIsComputed`) |
| …except | `System.exit`, a JVM crash, or an infinite loop in `try` |
| ⚠️ **`return` in `finally`** | **replaces** the try's result, **and swallows any exception**. `FinallyBehaviour.returnInFinallySwallowsTheException` throws `IllegalStateException` and returns `42`, with no trace. javac only warns. **Never return or throw from `finally`.** |
| Rethrowing | `throw e;` rethrows the same object: stack trace, type and message preserved |

---

## 4. try-with-resources and suppressed exceptions ⭐⭐⭐

Anything implementing **`AutoCloseable`** (files, streams, JDBC connections, HTTP clients,
locks you wrap) can be opened in the `try (…)` header and is closed automatically:

```java
try (var db = new TracingResource("db", log, false);
     var file = new TracingResource("file", log, false)) {
  db.use(false);
  file.use(false);
}
// log: open db, open file, use db, use file, close file, close db   ← REVERSE order
```

**What if both the body and `close()` fail?** In a hand-written `try`/`finally`, the `close()`
exception would *replace* the real one, and you'd debug the wrong problem. try-with-resources
keeps the **body's exception** and attaches the close failure as **suppressed**:

```java
thrown.getMessage()          // "file failed while in use"     ← the real problem
thrown.getSuppressed()[0]    // "file failed to close"         ← kept, not lost
```

(`aCloseFailureIsSuppressedBehindTheRealError`.) This is why try-with-resources beats a manual
`finally { x.close(); }`: it's shorter, closes in the right order, handles nulls, and never hides
the original error.

---

## 5. Designing your own exceptions ⭐⭐⭐

`MasternovaException` is the prototype for Phase 2:

```java
public abstract sealed class MasternovaException extends RuntimeException
    permits NotFoundException, VersionConflictException, ValidationException, RuleViolationException {
  private final String code;                                  // ⭐ stable, machine-readable
  protected MasternovaException(String code, String message, Throwable cause) {
    super(message, cause);                                    // ⭐ keep the cause
    this.code = code;
  }
}
```

**Checklist:**

1. **Unchecked** (`extends RuntimeException`) for domain errors.
2. A **stable `code`** (`"VERSION_CONFLICT"`). Clients and tests branch on it; the message is
   prose for humans and may change.
3. **Context as typed fields**, not baked into strings: `VersionConflictException(expected,
   actual)`, so the API can return them (`ProblemMapperTest.conflictCarriesBothVersionsForTheClient`).
4. **Constructors that accept a `cause`**, and always pass it when wrapping.
5. **Few types, organised by what the caller does with them** (not found, conflict, invalid,
   rule broken), not one class per message.
6. **Sealed**, so the mapping to HTTP status is an **exhaustive switch**
   (`ProblemMapper.toProblem`, note 02). A new kind can't silently become a 500.
7. **Report all problems at once** where it helps: `ValidationException` carries every field
   error, not just the first.

**Mapping to HTTP** (`ProblemMapper`, the plain-Java version of Phase 2's handler):

| Exception | Status | Code |
|---|---|---|
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ValidationException` | 400 | `VALIDATION_FAILED` + `errors[]` |
| `VersionConflictException` | 409 | `VERSION_CONFLICT` + versions |
| `RuleViolationException` | 422 | the rule's own code (`COUPON_EXPIRED`) |
| anything else | 500 | `INTERNAL`, generic text. **Never leak the message.** (`unexpectedErrorsNeverLeakTheirMessage`: a `password=…` in an NPE message stays server-side.) |

---

## 6. Exceptions vs return values ⭐⭐⭐

Exceptions are for the **exceptional**. They're slow-ish (they capture a stack trace), they're
invisible in the method signature (when unchecked), and they jump over code. For outcomes the
caller **expects and must handle**, return them:

| Situation | Use | Example here |
|---|---|---|
| "maybe there's no value" | `Optional<T>` | `CourseLookup.findById` |
| success or a known failure | `Result<T>` (note 04) / a sealed outcome (note 02) | `Result.attempt`, `PaymentOutcome` |
| many independent items, some bad | a **report** of successes + errors | `CourseImporter.ImportReport`: one bad CSV line doesn't stop 999 good ones |
| a bug or a broken invariant | an unchecked exception | `IllegalArgumentException` in a compact constructor |
| infrastructure failure the caller may handle | a checked exception (at the boundary) | `IOException` from `importFrom` |
| a request that breaks a business rule (→ HTTP 4xx) | a domain exception | `RuleViolationException` |

**Never use exceptions for normal control flow.** Don't loop until `IndexOutOfBoundsException`,
and don't parse with `try/catch` as your branching logic when a check is cheap.

---

## 7. Checked exceptions and lambdas ⭐⭐

`java.util.function` interfaces declare no exceptions, so this doesn't compile:

```java
paths.stream().map(Files::readString).toList();
→ error: incompatible thrown types IOException in functional expression
paths.stream().map(p -> Files.readString(p)).toList();
→ error: unreported exception IOException; must be caught or declared to be thrown
```

**Options:**

1. **Wrap in an unchecked exception** inside the lambda (or a helper), keeping the cause.
   `UncheckedIOException` exists exactly for this (`CourseImporter.importFromUnchecked`).
2. **Use a loop** when exceptions matter (note 03 §11).
3. **Make the exception a type parameter** of your own functional interface. `ThrowingSupplier<T,
   E extends Exception>` lets `Retry.withRetries` accept a lambda that throws `IOException`,
   and the caller still has to handle `IOException`: checking is preserved.

Inside `Retry`, note `catch (E e)` is illegal (`error: unexpected type`, because of erasure). We
catch `Exception` and cast, with a comment proving the cast is safe.

---

## 8. `InterruptedException` ⭐⭐

`Thread.sleep`, `BlockingQueue.take` and `Future.get` throw `InterruptedException` when another
thread asks this one to stop (shutdown, cancelled request, timeout). **Catching it clears the
thread's interrupt flag.** Swallow it, and the request to stop is lost: the app hangs on
shutdown.

```java
} catch (InterruptedException e) {
  Thread.currentThread().interrupt();          // ⭐ restore the flag
  throw new IllegalStateException("interrupted while waiting to retry", e);   // and stop
}
```

`RetryTest.interruptionStopsRetryingAndKeepsTheFlag` checks that the flag is still set
afterwards. Concurrency properly: task 1.7.

---

## 9. `Optional` done right ⭐⭐⭐

`Optional<T>` is a **return type** that says "there may be no value", in the type. It's not
a general null replacement.

### The API you'll use

| Create | |
|---|---|
| `Optional.ofNullable(x)` | `x` may be null → empty or present ✅ |
| `Optional.of(x)` | `x` must not be null, or NPE (`Optional.of(null)` throws) |
| `Optional.empty()` | explicitly nothing |

| Transform (only runs if present) | | Example here |
|---|---|---|
| `map(fn)` | value → another value | `findById(id).map(Course::title)` |
| `filter(pred)` | keep only if it matches | `.filter(Course::published)` in `priceIfPublished` |
| `flatMap(fn)` | fn returns an `Optional`; avoids `Optional<Optional<T>>` | `publishedOn` (null for drafts) |
| `or(() -> other)` | another `Optional`, computed only if empty (Java 9) | `findByIdOrSlug` |

| Get the value out | |
|---|---|
| `orElse(default)` | ⚠️ `default` is **computed even when not needed** |
| `orElseGet(() -> default)` | computed only when empty ✅ for anything expensive |
| `orElseThrow(() -> new NotFoundException(…))` | the domain exception → 404 ✅ (`getOrThrow`) |
| `orElseThrow()` | `NoSuchElementException`. Better than `get()` only because the name is honest. |
| `ifPresent(fn)` / `ifPresentOrElse(fn, other)` | side effects |
| `isPresent()` / `isEmpty()` | prefer the methods above to `if (opt.isPresent()) opt.get()` |

**The `orElse` trap**, proven by `orElseEvaluatesItsArgumentEvenWhenNotNeeded`:

```java
opt.orElse(loadDefaultFromDatabase());           // runs the query EVERY time, even when opt has a value
opt.orElseGet(() -> loadDefaultFromDatabase());  // runs it only when opt is empty
```

### Rules ⭐⭐⭐

| ✅ Do | ❌ Don't |
|---|---|
| return `Optional<T>` from lookups (`findById`) | use `Optional` for **fields**: it isn't `Serializable`, and it's clumsy for JPA and Jackson |
| chain `map` / `filter` / `orElse…` | take `Optional` as a **method parameter**: overload, or accept a nullable value |
| `orElseThrow(() -> domainException)` | call `get()` without checking: it's just an NPE with a different name |
| return an **empty collection** for "no results" | return `Optional<List<T>>` |
| `Optional.ofNullable` when wrapping a maybe-null | `Optional.of(maybeNull)` |
| | ever return `null` **from** a method declared to return `Optional`! |

Spring Data does exactly this: `courseRepository.findById(id)` returns `Optional<Course>`, and
service code reads `findById(id).orElseThrow(() -> new NotFoundException("Course", id))`.

---

## 10. Walkthrough of the code ⭐⭐

| File | Shows |
|---|---|
| [`MasternovaException`](../lab/src/main/java/com/masternova/java/exceptions/MasternovaException.java) + subclasses | A sealed, unchecked hierarchy; a stable code; cause-carrying constructors; typed context fields (`expected`/`actual`); `ValidationException` with a `FieldError` record list |
| [`ProblemMapper`](../lab/src/main/java/com/masternova/java/exceptions/ProblemMapper.java) | Exhaustive switch → status + code + extras; `unexpected` never leaks |
| [`CourseImporter`](../lab/src/main/java/com/masternova/java/exceptions/CourseImporter.java) | `throws IOException`; try-with-resources; a narrow `catch (IllegalArgumentException)` covering `NumberFormatException` and `Money`'s validation; errors as data (`ImportReport`); the `UncheckedIOException` wrapper |
| [`TracingResource`](../lab/src/main/java/com/masternova/java/exceptions/TracingResource.java) | `AutoCloseable`; drives the close-order and suppressed-exception tests |
| [`FinallyBehaviour`](../lab/src/main/java/com/masternova/java/exceptions/FinallyBehaviour.java) | `finally` after `return`; the return-in-finally trap |
| [`CourseLookup`](../lab/src/main/java/com/masternova/java/exceptions/CourseLookup.java) | `Optional` creation, `map`/`filter`/`flatMap`/`or`/`orElseThrow` |
| [`Retry`](../lab/src/main/java/com/masternova/java/exceptions/Retry.java) + [`ThrowingSupplier`](../lab/src/main/java/com/masternova/java/exceptions/ThrowingSupplier.java) | Exception type as a generic parameter; rethrowing the same exception; `InterruptedException` done right |

**Tests worth reading:** `goodLinesAreImportedAndBadLinesAreReportedNotThrown` (a text-block
CSV, with `@TempDir` making a throwaway folder), `aCloseFailureIsSuppressedBehindTheRealError`,
`returnInFinallySilentlyDiscardsTheException`, `orElseEvaluatesItsArgumentEvenWhenNotNeeded`,
`interruptionStopsRetryingAndKeepsTheFlag`.

---

## 11. Exceptions in Spring / Masternova ⭐⭐⭐

| Where | How |
|---|---|
| **Phase 2: `GlobalExceptionHandler`** | `@RestControllerAdvice` with one `@ExceptionHandler(MasternovaException.class)` doing what `ProblemMapper` does, but returning Spring's `ProblemDetail` (RFC 9457). Already exists in the skeleton for the 500 case. |
| **Services** | `repository.findById(id).orElseThrow(() -> new NotFoundException("Course", id))` |
| **Validation** | Bean Validation (`@Valid`) failures → `MethodArgumentNotValidException` → mapped to 400 with `errors[]`, the same shape as `ValidationException` |
| **Transactions** | Domain exceptions are unchecked, so `@Transactional` **rolls back**. A checked exception would **commit** unless you set `rollbackFor`. |
| **Optimistic locking (Phase 6)** | JPA throws `ObjectOptimisticLockingFailureException`; we translate it to `VersionConflictException` → 409 |
| **External calls (Phase 9)** | Retries with backoff (Resilience4j, or `Retry` here as the idea). Retry only **idempotent**, **transient** failures. |
| **Logging** | Log **once**, at the boundary (the handler), with the trace id. Never log-and-rethrow in every layer. |

---

## 12. Anti-patterns ⭐⭐⭐

| ❌ Anti-pattern | Why it hurts | ✅ Instead |
|---|---|---|
| `catch (Exception e) {}` (swallowing) | the bug vanishes; data silently wrong | handle it, or let it propagate |
| `catch (Exception e) { log.error(e); throw e; }` in every layer | the same stack trace logged 5 times | log once at the boundary |
| `throw new RuntimeException("failed")` while discarding `e` | the root cause is lost | `throw new X("failed", e)` |
| catching `Exception`/`Throwable` broadly | catches bugs (NPE) and `Error`s you can't handle | catch the narrowest type |
| `return` / `throw` inside `finally` | swallows the real exception (§3) | never |
| manual `finally { stream.close(); }` | wrong order, null checks, masks errors | try-with-resources (§4) |
| exceptions for control flow | slow, unreadable | checks, `Optional`, `Result` |
| a checked exception from `@Transactional` without `rollbackFor` | the transaction **commits** on failure | unchecked domain exceptions |
| `catch (InterruptedException e) {}` | the shutdown signal is lost | restore the flag (§8) |
| `optional.get()` / `Optional` fields or params / returning `null` for an `Optional` | defeats the purpose | §9 rules |
| `orElse(expensiveCall())` | runs every time | `orElseGet` |
| exposing `e.getMessage()` in a 500 response | leaks internals (SQL, paths, secrets) | a generic message, details in the log |

---

## 13. Interview Q&A ⭐⭐⭐

**Q1. Checked vs unchecked exceptions? When do you use each?**
Checked exceptions extend `Exception` and are enforced by the compiler; they're for recoverable
external conditions at boundaries. Unchecked ones extend `RuntimeException`; they're for
programming errors and domain-rule violations. Modern Spring code is mostly unchecked.

**Q2. `Error` vs `Exception`?**
`Error` is a JVM-level problem (OOM, stack overflow) that you shouldn't catch. `Exception` is
something application code may handle.

**Q3. Does `finally` always run?**
Yes, after `return` and after exceptions. The exceptions are `System.exit`, a JVM crash, or a
`try` that never finishes. A `return` in `finally` overrides the result and swallows
exceptions.

**Q4. What is try-with-resources? What are suppressed exceptions?**
It automatically closes `AutoCloseable` resources in reverse order. If both the body and
`close()` throw, the body's exception propagates, and the close exception is attached via
`getSuppressed()`.

**Q5. How do you design custom exceptions?**
Unchecked, a stable error code, typed context fields, constructors that accept a cause, a few
types grouped by the caller's reaction, mapped centrally to HTTP status.

**Q6. Will `@Transactional` roll back if you throw a checked exception?**
No. By default it rolls back only on `RuntimeException` and `Error`. Use
`rollbackFor = Exception.class`, or throw unchecked exceptions.

**Q7. How do you handle checked exceptions inside lambdas?**
Wrap them in an unchecked exception that keeps the cause (`UncheckedIOException`), use a loop,
or define a functional interface with a generic exception type parameter.

**Q8. What should you do when catching `InterruptedException`?**
Restore the interrupt flag with `Thread.currentThread().interrupt()` and stop or propagate.
Never swallow it.

**Q9. `orElse` vs `orElseGet`?**
`orElse(x)` evaluates `x` eagerly, every time. `orElseGet(supplier)` evaluates it only when the
Optional is empty.

**Q10. Where should you not use `Optional`?**
Fields, method parameters, collections (return an empty collection instead), and anywhere
`null` would be returned in its place.

**Q11. Exceptions or return values for validation?**
For expected, recoverable outcomes (a bad CSV row, a coupon that doesn't apply), return values:
`Optional`, `Result`, a report. Throw exceptions for violated invariants and for errors that
must stop the request (mapped to 4xx/5xx).

---

## 14. Play with it in `jshell` ⭐

```bash
cd patterns/lab && ./mvnw -q compile && jshell --class-path target/classes
```

```java
jshell> import com.masternova.java.exceptions.*
jshell> ProblemMapper.toProblem(new VersionConflictException(7, 8))
jshell> FinallyBehaviour.returnInFinallySwallowsTheException()        // 42: where did the exception go?
jshell> var lookup = new CourseLookup(com.masternova.java.streams.CatalogData.courses())
jshell> lookup.findByIdOrSlug("docker-in-practice")
jshell> lookup.publishedOn("c8")
jshell> lookup.getOrThrow("nope")
jshell> java.util.Optional.of(null)
jshell> try { throw new java.io.IOException("disk"); } catch (Exception e) { throw new RuntimeException("wrapped", e); }
```

---

## 15. 30-second recall

- **The hierarchy:**
  - `Throwable` splits into `Error` (don't catch) and `Exception`.
  - `Exception` itself is checked; its `RuntimeException` branch is unchecked.
- **Checked vs unchecked:**
  - Checked: boundaries the caller can handle (`IOException`).
  - Unchecked: bugs and domain rules.
  - Spring: unchecked. `@Transactional` rolls back only on unchecked exceptions.
- **`finally`:** always runs, even after `return`. Never return or throw from it (that
  swallows the real exception).
- **try-with-resources:** closes in reverse order; the body's exception wins and the close
  failure is suppressed.
- **Custom exceptions:**
  - Unchecked, a stable `code`, typed context, keep the `cause`.
  - Sealed, so the HTTP mapping is exhaustive. Never leak 500 messages.
- **Expected outcomes are data:** `Optional`, `Result`, a report. Exceptions are for the
  exceptional.
- **Lambdas and checked exceptions:** wrap them (`UncheckedIOException`) or use a
  `ThrowingSupplier<T, E>`.
- **`InterruptedException`:** restore the flag, then stop.
- **`Optional`:**
  - A return type only. Chain `map`/`filter`/`flatMap`/`or`.
  - Use `orElseGet` for expensive defaults and `orElseThrow(domainEx)` for missing values.
  - Never `get()`, never fields or parameters.
- **Next:** [06 — OOP: composition over inheritance](README.md) (task 1.6).
