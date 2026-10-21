# ADR-0023 — Course ratings: an exact per-star tally locked by every review write, pushed to catalog in the same transaction, recounted nightly

**Status:** accepted · **Date:** 2026-10-21 · **Deciders:** Nimai
**Context links:** [`docs/lld/engagement.md`](../lld/engagement.md) §3–§8 · [`catalog.md`](../lld/catalog.md) §2 (denormalised counters) · Phase 10's `enrollment_count` ([`progress.md`](../lld/progress.md))

## Context

`course.rating_average` (`NUMERIC(3,2)`) and `rating_count` sit on catalog's row, so the list
pages can show and sort by them (`HIGHEST_RATED`, `course_published_rating_idx`). Phase 11 adds
the reviews that feed them, in a new `engagement` module. Learners create, edit and delete
reviews (one per course each), concurrently and sometimes twice (a double-click, a retry). Rows
also disappear without any Java running: deleting a user cascades to their reviews.

The roadmap planned this as an Observer: engagement publishes an event, catalog updates its
columns.

## Decision

1. **The exact state is five integers.** `course_rating` holds `stars_1 … stars_5` for each
   reviewed course. Count, sum and average are *derived* (`RatingTally`, a record), never stored
   beside the stars, so they can't disagree.
2. **Every review write locks the tally row first.** `INSERT … ON CONFLICT DO NOTHING` (so a
   first review has a row to lock), then `SELECT … FOR UPDATE`, then read the learner's old
   rating, then write the review and the new tally. All writes for one course serialise on that
   row, so concurrent first reviews and double-submits each move the tally exactly once.
3. **Catalog's columns move in the same transaction**, through `CatalogApi.updateRating(courseId,
   count, average)`: a native `UPDATE` that doesn't bump the course's `version` (it isn't an
   edit), on columns now mapped `updatable = false` (an instructor's save can't write back a
   stale average). The same shape as `adjustEnrollmentCount` (Phase 10), except that the value is
   absolute: it comes from the locked tally.
4. **Nightly reconciliation** (`RatingReconcileJob`, ShedLock, ~3 min after start then every 24 h):
   one `GROUP BY` finds tallies that differ from the reviews; each is repaired in its own
   transaction **under the same lock, re-counted after the lock is held**, and pushed to
   catalog; then catalog's columns are compared with every tally, and a rating on a course with
   no tally at all is reset from a recount (normally to 0). A non-zero count is logged at WARN.
5. **Not an Observer.** An event would put the card's number in a second transaction that can
   fail, lag or be replayed, for a write that is already in a transaction catalog's API can
   join. The reconciler covers what no transaction sees.

## Consequences

- **Exact under concurrency and retries** (`ReviewIT`: 20 concurrent `PUT`s by one learner → 1
  review counted once; 10 learners → count 10, sum exact).
- **A hot course serialises its review writes.** Each holds the lock for a few statements (< 5 ms).
  Reviews arrive far slower than heartbeats; if a course ever saw ~100 reviews/s, the answer
  would be ADR-0022's buffer, not a different lock.
- **Engagement depends on catalog and enrollment** (`CatalogApi`, the new `EnrollmentApi`); a
  dependency in the other direction would be a cycle, so catalog never asks engagement anything.
- **Drift is bounded by a day** for writes around the code (cascades, manual SQL), and visible
  (the WARN). The reconciler doesn't erase a review committed while it waits: it counts after the
  lock, in a new statement (`RatingReconcileIT`).
- `Course.updateRatingSummary` (a Phase 5 placeholder that set the columns through the entity, now
  silently lost on a loaded course) is gone. Its rule (0–5, count and average both zero or both
  not) is `Course.checkedRatingAverage`, applied by `updateRating`; the test data builder sets a
  summary only on a course not yet saved.

## Alternatives considered

- **`AVG(rating)` at read time:** on every list page, per card; and the sort needs an indexed column.
- **Incremental average on catalog's columns:** `NUMERIC(3,2)` rounds at each step; the stored
  average drifts from the truth with every review.
- **A Postgres trigger on `review`:** exact and cascade-proof, but the rule lives in SQL no Java
  test names, and it would write catalog's table from engagement's trigger.
- **An event + listener (Observer):** see Decision §5.
