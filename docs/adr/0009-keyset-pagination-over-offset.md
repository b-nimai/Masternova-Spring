# ADR-0009 — Keyset (cursor) pagination over `LIMIT/OFFSET`

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai
**Context links:** [`docs/lld/catalog.md`](../lld/catalog.md) · [API conventions §2](../api/conventions.md#2-cursor-pagination) · NestJS Masternova ADR-0015 (same decision, re-derived for Spring Data)

## Context

The catalog is an endless, filterable list that people scroll while instructors keep publishing
into it. Every later list (my courses, orders, reviews, Q&A) has the same shape. Spring Data's
default is `Pageable` → `LIMIT ? OFFSET ?` plus a `COUNT(*)` for the `Page` total.

`OFFSET` has two problems:

1. **It gets slower with depth.** `OFFSET 10000` makes Postgres produce and throw away 10,000
   rows before returning 20. Page 500 costs 500 times page 1.
2. **It is wrong under concurrent writes.** If a course is published while you read page 1,
   every row shifts down by one: the last row of page 1 shows up again as the first row of
   page 2. A delete shifts them up, and a row is silently skipped.

`Page<T>` adds a third cost: a `COUNT(*)` over the whole matching set on every request, to show
a number nobody needs in an infinite scroll.

## Decision

1. **Lists page by keyset.** The client sends an opaque `cursor`; the server returns
   `{ "items": [...], "nextCursor": "…" | null }`. **No `total`.**
2. **Every sort ends in the primary key** (`publishedAt DESC, id DESC`), so the order is total
   and two rows can never tie across a page boundary.
3. **Sort keys are non-nullable** in the lists that use them. `published_at` is `NOT NULL`
   whenever the status is `PUBLISHED` (a `CHECK` constraint), and the public list only shows
   published courses. That removes the `NULLS LAST` special case keysets otherwise need.
4. **Mechanics: Spring Data's `ScrollPosition.keyset()` + `Window<T>`**, through
   `JpaSpecificationExecutor.findBy(spec, q -> q.sortBy(…).limit(n).scroll(position))`. It
   composes with our Specifications, fetches `limit + 1` rows to know whether there's a next
   page, and builds the "after this row" predicate.
5. **The cursor is ours, not Spring's.** `CourseCursor` encodes the sort name, the typed key
   values and the id as base64url. A cursor used with a different sort is rejected (400
   `INVALID_CURSOR`) instead of silently returning a wrong page.

## Consequences

- **Positive:**
  - Page 500 costs about the same as page 1: an index range scan that **starts** at the cursor.
  - No duplicates, no skips while courses are published or archived during scrolling.
  - No `COUNT(*)` per request.
- **Negative:**
  - No "jump to page 37" and no "1–20 of 4,312". The UI is an infinite scroll; that's the
    product decision anyway.
  - Each sort needs an index whose column order matches it (task 5.9 measures every one).
  - Spring Data builds the keyset predicate as an `OR` chain:
    `published_at < ? OR (published_at = ? AND id < ?)`. Postgres' row comparison
    `(published_at, id) < (?, ?)` can be an index **start** condition; the `OR` form may not.
    5.9 measures both on 10,000 courses and records whether a native query is worth it.

## Outcome (measured in 5.9, 2026-10-02)

On 10,000 seeded courses ([`docs/db/indexes.md`](../db/indexes.md)):

| Page 250 of NEWEST | ms |
|---|---:|
| `OFFSET 4980` (what `Pageable` sends), with every index present | 15.7 |
| Spring Data's keyset OR chain | 2.35 (the scan starts at the top and filters) |
| row comparison `(published_at, id) < (…)` (hand-written) | 0.19 |
| ⭐ OR chain **+ redundant `published_at <= :key`** (shipped: `CourseSpecifications.keysetBound`) | **0.155** |

The open question in *Consequences* is answered without leaving Spring Data: the redundant bound
gives Postgres the start condition the OR chain lacks. `CatalogIndexesIT` pins the plan shape.

## Alternatives rejected

| Option | Why not |
|---|---|
| `Pageable` + `Page<T>` (`OFFSET` + `COUNT`) | slow with depth, wrong under concurrent writes, plus a count per request |
| `Slice<T>` (`OFFSET` without the count) | removes the count, keeps both `OFFSET` problems |
| a hand-rolled keyset predicate per query | re-implements what `KeysetScrollPosition` already does; worth it only for the row-comparison form, and only if 5.9's numbers say so |
| a server-side cursor (`DECLARE CURSOR`) or a cached result set | holds a transaction or memory per browsing user; doesn't survive a load balancer |
| exposing Spring's `KeysetScrollPosition` keys to the client | leaks property names, loses types through JSON, and can't detect a cursor reused with another sort |
