# 13 — Optimistic locking: lost updates, `@Version`, and an aggregate's version

> **One-liner:** two people (or two tabs) read version 7 and both save; without a guard the second
> save silently erases the first, a **lost update**. JPA's `@Version` turns the save into
> `UPDATE … WHERE id = ? AND version = 7`, so the second one updates **0 rows** and fails. Send
> `expectedVersion` from the client for a precise 409, and make the **root's version cover the
> whole aggregate**.

**Roadmap:** task 6.6 · **Last updated:** 2026-10-03 · **Prev:** [12 — JPA mapping & fetching](12-jpa-mapping-and-fetching.md) · **ADR:** [0010](../../docs/adr/0010-optimistic-concurrency-with-version.md)
**Real code:** [`Course`](../../backend/api/src/main/java/com/masternova/api/catalog/domain/Course.java) (`@Version`, `touch`), [`CourseAccess.forEditing`](../../backend/api/src/main/java/com/masternova/api/catalog/application/CourseAccess.java), [`GlobalExceptionHandler`](../../backend/api/src/main/java/com/masternova/api/platform/web/GlobalExceptionHandler.java) (`ObjectOptimisticLockingFailureException` → 409)
**Tests:** [`CourseAuthoringIT`](../../backend/api/src/test/java/com/masternova/api/catalog/web/CourseAuthoringIT.java) (10 concurrent saves → 1 winner), [`CourseLifecycleIT`](../../backend/api/src/test/java/com/masternova/api/catalog/web/CourseLifecycleIT.java) (a stale detached copy rejected)

| # | Section | Priority |
|---|---|---|
| 1 | [The lost update](#1-the-lost-update-) | ⭐⭐⭐ |
| 2 | [How `@Version` works](#2-how-version-works-) | ⭐⭐⭐ |
| 3 | [`expectedVersion`: a precise 409 for the client](#3-expectedversion-a-precise-409-for-the-client-) | ⭐⭐⭐ |
| 4 | [The root's version covers the aggregate](#4-the-roots-version-covers-the-aggregate-) | ⭐⭐⭐ |
| 5 | [Optimistic vs pessimistic](#5-optimistic-vs-pessimistic-) | ⭐⭐ |
| 6 | [Testing concurrency for real](#6-testing-concurrency-for-real-) | ⭐⭐ |
| 7 | [Common mistakes](#7-common-mistakes-) | ⭐⭐⭐ |
| 8 | [Interview Q&A](#8-interview-qa) | ⭐⭐⭐ |
| 9 | [30-second recall](#9-30-second-recall) | ⭐⭐⭐ |

---

## 1. The lost update ⭐⭐⭐

```text
Tab A: GET course (title "K8s", v7)              Tab B: GET course (title "K8s", v7)
Tab A: PUT title "Kubernetes"  → saved                                      │
                                                 Tab B: PUT subtitle "Hands-on" (title still "K8s")
                                                        → saved: the title is back to "K8s" ❌
```

Nobody gets an error; the description "reverts" days later. Transactions don't prevent this: each
PUT is its own short transaction, and both are perfectly valid. The data race is between
**read (in the browser)** and **write (much later)**, across transactions. Database isolation
levels can't see it; a **version** can.

## 2. How `@Version` works ⭐⭐⭐

```java
@Entity
public class Course {
  @Version private Long version;   // null = new; then 0, 1, 2 … — Hibernate manages it
}
```

On every update of the row, Hibernate:

```sql
UPDATE course SET title = ?, …, version = 8 WHERE id = ? AND version = 7
```

- **1 row updated:** you held the latest version; the entity's `version` becomes 8.
- **0 rows updated:** someone changed the row since you read it → `OptimisticLockException`
  (Hibernate's `StaleObjectStateException`) → Spring translates it to
  `ObjectOptimisticLockingFailureException`, the transaction rolls back **entirely**, and
  `GlobalExceptionHandler` answers 409 `VERSION_CONFLICT`.

The same check guards **merging a detached entity**: `CourseLifecycleIT` loads a course, lets
another transaction archive it, then saves the stale copy → `ObjectOptimisticLockingFailureException`.

Never set `version` yourself, and never use `updated_at` as the token: two writes inside one clock
tick tie; a counter can't.

## 3. `expectedVersion`: a precise 409 for the client ⭐⭐⭐

The browser's copy was read in an *earlier* transaction, so the entity you load now is already at
the newest version, and `@Version` alone would happily overwrite the browser's stale view. The
client must say which version it edited:

```java
// CourseAccess.forEditing
Course course = forAuthoring(courseId, actor);
course.requireEditable();
if (course.version() != expectedVersion) {
  throw ConflictException.versionConflict(expectedVersion, course.version());   // 409 with BOTH numbers
}
```

```json
{ "code": "VERSION_CONFLICT", "expectedVersion": 0, "currentVersion": 1 }
```

Two layers, two jobs:

| Layer | Catches | Response |
|---|---|---|
| the `expectedVersion` pre-check | the stale tab (read long ago) | 409 with `expectedVersion` / `currentVersion` |
| `@Version` at flush | two requests that read the **same** version at the same moment and both passed the pre-check | 409 (no versions: only the DB knew) |

`CourseAuthoringIT.tenConcurrentSavesOfOneVersionHaveExactlyOneWinner` fires 10 PUTs with
`expectedVersion: 0` at once: **exactly one 200**, nine 409s, version ends at 1, and the stored
title is the winner's.

## 4. The root's version covers the aggregate ⭐⭐⭐

`@Version` lives on the **course** row. Rename a *lecture* and Hibernate updates only the
`lecture` row, so the course's version **doesn't move** and a stale tab's next save wins. Fix it
in the aggregate root, once:

```java
/** Every mutation through the root calls this, so the course's version covers the whole aggregate. */
void touch(Instant now) {
  updatedAt = micros(now);    // dirties the course row → UPDATE course … version = version + 1
}

public void changeDetails(…, Instant now) { requireEditable(); …; touch(now); }
public void confirmPrice(Money p, Instant now) { requireEditable(); …; touch(now); }
public void transition(CourseAction a, Instant now) { …; touch(now); }
// and every curriculum command (6.4) goes through methods that touch
```

Alternative: `LockModeType.OPTIMISTIC_FORCE_INCREMENT` on the load. It works, but it's chosen per
query, and the next write path written next year forgets it. A mutator that touches can't be
skipped, because it's the only way to mutate.

## 5. Optimistic vs pessimistic ⭐⭐

| | Optimistic (`@Version`) | Pessimistic (`SELECT … FOR UPDATE`) |
|---|---|---|
| assumes | conflicts are rare | conflicts are common |
| holds | nothing between read and write | a row lock for the transaction |
| on conflict | the loser fails (409) and retries | the second waits |
| fits | **human editing** (think time of minutes) | short, hot, server-side critical sections (the outbox claim uses `FOR UPDATE SKIP LOCKED`) |
| never | — | hold a lock across a human's think time: it's held until the browser crashes |

Transitions in Masternova use optimistic locking too: two transitions each legal from what its
caller read (archive vs withdraw) → the second commit fails on the version.

## 6. Testing concurrency for real ⭐⭐

```java
CountDownLatch start = new CountDownLatch(1);
try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
  for (int i = 0; i < 10; i++) {
    results.add(pool.submit(() -> { start.await(); return putDetails(id, 0, "Tab " + i); }));
  }
  start.countDown();                   // ⭐ release all 10 at once
}
assertThat(statuses).containsOnly(200, 409);
assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
```

- A **latch** makes the requests overlap; otherwise they run one after another and the test proves
  nothing.
- Assert the **invariant** (exactly one winner, its data stored), not an order: which thread wins
  is up to the scheduler.
- It must run against **real Postgres** (Testcontainers): H2's locking differs.

## 7. Common mistakes ⭐⭐⭐

| Mistake | Symptom | Fix |
|---|---|---|
| no `expectedVersion` from the client | stale tabs overwrite silently (the entity you load is always fresh) | the client sends the version it edited |
| `@Version` only on the root, children changed directly | a lecture edit doesn't bump the version | mutate through the root, which touches |
| setting `version` by hand / copying it from a DTO onto a managed entity | Hibernate ignores it or you defeat the check | compare, never assign |
| catching `OptimisticLockException` and retrying blindly on the server | the server re-applies a stale user's edit | 409 to the client; the *human* decides |
| `updated_at` as the token | ties within a clock tick | a counter |
| pessimistic lock across a user session | locks held for minutes; deadlocks | optimistic for human edits |
| testing concurrency sequentially | green test, nothing proven | a latch + a pool, real database |

## 8. Interview Q&A

- **Q:** What is a lost update and how do you prevent it?
  **A:** Two clients read the same state and both write; the second silently overwrites the first.
  Optimistic locking: a version column checked in the `UPDATE`'s `WHERE`; the loser updates 0 rows
  and gets an error (409) instead of overwriting.
- **Q:** Why does the client need to send the version if JPA has `@Version`?
  **A:** The entity is loaded fresh in the write's transaction, so it's always at the newest
  version. Only the client knows which version it was *looking at*. `@Version` still matters: it
  catches two requests that read the same version at the same instant.
- **Q:** Your aggregate has children. A child changes; does the version move?
  **A:** Not by itself: only the root's row carries `@Version`. Make every mutation go through the
  root and touch it (or use `OPTIMISTIC_FORCE_INCREMENT`).
- **Q:** Optimistic or pessimistic locking?
  **A:** Optimistic for user-facing edits with think time (conflicts rare, no locks held).
  Pessimistic for short, contended server-side work, e.g. claiming queue rows with
  `FOR UPDATE SKIP LOCKED`.
- **Q:** How do you test it?
  **A:** N threads, a start latch, the same expected version, real database; assert exactly one
  success and that its data is what's stored.

## 9. 30-second recall

- **Lost update** = read-then-write across transactions; isolation levels don't see it; a version does.
- `@Version` → `UPDATE … WHERE version = ?`; 0 rows → `ObjectOptimisticLockingFailureException` → 409.
- **Client sends `expectedVersion`** → pre-check 409 `{expectedVersion, currentVersion}`;
  `@Version` catches the same-instant race. Proven: 10 concurrent → 1 winner.
- **Root's version covers the aggregate:** every mutator calls `touch(now)`.
- Optimistic for humans, pessimistic (`SKIP LOCKED`) for hot server-side claims.
