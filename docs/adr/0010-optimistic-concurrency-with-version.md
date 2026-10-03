# ADR-0010 — Optimistic concurrency: JPA `@Version` + `expectedVersion` in the body

**Status:** accepted · **Date:** 2026-10-03 · **Deciders:** Nimai
**Context links:** [`docs/lld/catalog-authoring.md`](../lld/catalog-authoring.md) · [API conventions §3](../api/conventions.md#3-optimistic-concurrency) · NestJS Masternova (same decision: a version counter claimed conditionally)

## Context

Instructors edit a course for days, in more than one tab, with autosave. Two saves of the same
starting state are normal, not exceptional. Without a guard the later save silently overwrites the
earlier one (last write wins), and the lost edit is noticed only when the content "reverts".

The course is an aggregate: a change to one lecture is a change to the course. The guard must
cover the whole aggregate, not just the `course` row.

## Decision

1. **The token is `course.version`**, a JPA `@Version` counter (already in V6). Not `updated_at`:
   two writes in one clock tick tie; a counter can't.
2. **Clients send `expectedVersion` in the request body** of every content write (details,
   pricing, curriculum commands, undo, redo). Every response carries the new `version`.
3. **The service pre-checks** `course.version() == expectedVersion` and answers a precise
   **409 `VERSION_CONFLICT` `{expectedVersion, currentVersion}`** (`ConflictException.versionConflict`).
4. **`@Version` closes the race the pre-check can't**: two requests that both read version 7 both
   pass the check; at flush Hibernate issues `UPDATE course … WHERE id = ? AND version = 7`, one
   updates 0 rows, `ObjectOptimisticLockingFailureException` rolls its whole transaction back and
   becomes the same 409 (`GlobalExceptionHandler`).
5. **The root's version covers the aggregate.** Every mutation through `Course` calls `touch(now)`,
   which dirties the course row, so a lecture rename bumps the course's version too.
6. **Lifecycle transitions take no `expectedVersion`** (API conventions §3). They re-read the
   aggregate and re-run their guards (state machine + publish gate). `@Version` still rejects a
   concurrent second transition, and every transition bumps the version, so open tabs go stale.

## Consequences

- **Positive:**
  - No silent lost updates, from two tabs or two people (an admin and the instructor).
  - No locks held while a human thinks.
  - One mechanism (`@Version`) for content writes and transitions.
- **Negative:**
  - A conflicting client must reload and re-apply. The Angular editor shows a dialog (6.8).
  - Every content write touches the course row, so edits to one course serialise on it. Fine for
    one instructor's course; it would matter only for a document edited by many people at once.

## Addendum (6.4, 2026-10-03): the edit transaction locks the course row first

`CurriculumIT` found a **deadlock** with 10 concurrent edits of one version. Each transaction
inserted its section row first (Hibernate flushes inserts before the versioned `UPDATE course`).
Under the deferred position constraint, T1 waited at commit on T2's uncommitted row while T2 waited
on T1's course-row lock. Every content write now begins with `CourseRepository.lockById`
(`SELECT … FOR UPDATE` on the course row), the same lesson as the NestJS "claim is the first
statement". The lock lasts the edit's few milliseconds, never a user's think time: the second editor
waits, then sees the new version and gets the clean 409. Result: 10 concurrent → 1 winner, 9 × 409,
no deadlock.

## Alternatives rejected

| Option | Why not |
|---|---|
| last write wins | silent data loss |
| `updated_at` as the token | ties within a clock tick |
| pessimistic `SELECT … FOR UPDATE` held across the editing session | a lock held for human think time; held until the browser crashes |
| `ETag` + `If-Match` headers | equivalent semantics; the forms already hold the version as data, and §3 of the conventions put it in the body. Could be added later as a second spelling. |
| `LockModeType.OPTIMISTIC_FORCE_INCREMENT` | correct, but chosen per query; a mutator-level `touch()` can't be forgotten by a new write path |
| a hand-written conditional `UPDATE … WHERE version = ?` claim | it's the statement `@Version` already emits |
