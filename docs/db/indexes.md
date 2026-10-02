# Catalog indexes — measured, not guessed

**Last updated:** 2026-10-02 · **Task:** 5.9 · **Migration:** [`V8__catalog_indexes.sql`](../../backend/api/src/main/resources/db/migration/V8__catalog_indexes.sql) · **LLD:** [`../lld/catalog.md`](../lld/catalog.md) §9 · **ADR:** [0009 keyset pagination](../adr/0009-keyset-pagination-over-offset.md)

> **One-liner:** every list query the catalog sends was measured with `EXPLAIN ANALYZE` on 10,000
> seeded courses **before** an index existed (V6 had none on purpose), each candidate index was
> measured, and only the ones that changed a plan were shipped. Two findings changed code, not
> just the schema: Spring Data's keyset predicate **couldn't seek**, and an expression index
> **without `ANALYZE`** picked a worse plan.

## 1. How to reproduce

```bash
make up && make api            # Flyway applies V1…V8
make seed COURSES=10000        # docs/db/seed-catalog.sql: deterministic (setseed), re-runnable
python3 docs/db/measure_catalog.py "my label"
```

- **Dataset:** 10,000 courses (9,039 published, 961 drafts), 20 instructors, 12 child categories,
  30,000 sections, 120,000 lectures. Postgres 17 (`pgvector/pgvector:pg17`), default settings
  (`work_mem` 4 MB), on a laptop.
- **Method:** each query runs 7 times with `EXPLAIN (ANALYZE, BUFFERS)`; the table shows the
  **median** execution time (warm cache: what a busy catalog sees) and the plan's main nodes.
- **The SQL is Hibernate's own**, captured with `logging.level.org.hibernate.SQL=DEBUG` and
  parameters filled in (`measure_catalog.py`). For example, a keyset page:

```sql
select c1_0.id, …, c2_0.name, … from course c1_0 join category c2_0 on c2_0.id=c1_0.category_id
where c1_0.status=? and 1=1
  and (c1_0.published_at<? or c1_0.published_at=? and c1_0.id<?)     -- ← Spring Data's keyset
order by c1_0.published_at desc, c1_0.id desc
fetch first ? rows only                                               -- limit + 1 = 21
```

(`1=1` is the `fetchingCategory()` Specification: it restricts nothing and only adds the join.)

## 2. Before and after

| # | Query | Before (V6) ms | After (V8) ms | × | Plan after |
|---|---|---:|---:|---:|---|
| Q1 | browse, NEWEST, page 1 | 8.510 | **0.139** | 61× | Index Scan `course_published_newest_idx` → Nested Loop category_pkey. **No Sort node.** |
| Q2 | page 250, keyset as Spring Data emits it (OR chain) | 4.999 | 2.354 | 2× | same index, but the scan starts at the **top** and filters ~5,000 rows |
| Q2b | page 250, OR chain **+ redundant bound** (shipped) | — ¹ | **0.155** | 32× vs Q2 before | same index, `Index Cond: (published_at <= …)`: the scan **starts at the cursor** |
| Q2r | page 250, row comparison `(published_at, id) < (…)` (hand-written) | 5.100 | 0.190 | 27× | same index; what JPA Criteria can't express |
| Q2o | page 250, `OFFSET 4980` (what `Pageable` sends) | 15.591 | 15.736 | 1× | Seq Scan → Sort **external merge (disk)**. No index fixes OFFSET. |
| Q3 | category tree (4 categories), NEWEST | 4.083 | **0.321** | 13× | the NEWEST index + a category filter |
| Q4 | HIGHEST_RATED | 9.418 | **0.188** | 50× | Index Scan `course_published_rating_idx` |
| Q5 | PRICE_LOW | 9.014 | **0.175** | 52× | Index Scan `course_published_price_idx` |
| Q5h | PRICE_HIGH | 8.933 | **0.178** | 50× | Index Scan **Backward** on the same price index |
| Q6 | title search, common word ('kube', 5 % of rows) | 5.635 | **0.677** | 8× | NEWEST index + filter (21 hits come quickly) |
| Q6x | title search, no match ('xyzzy') | 4.924 ² | **0.123** | 40× | Bitmap Index Scan `course_title_trgm_idx` |
| Q7 | instructor's own list | 2.422 | **0.161** | 15× | Index Scan `course_instructor_updated_idx` |
| Q8 | course page: course + category + sections | 0.164 | 0.206 | — | already served by `course_slug_key` + `section_position_uq` |
| Q8b | course page: lectures (batch) | 0.137 | 0.125 | — | already served by `lecture_position_uq` |

¹ Q2b was added to the harness after the "before" run; without an index it's the same seq scan
as Q2. ² Measured with the other candidates present but no trigram index (a seq scan either way).

**Q8 / Q8b are kept on purpose:** an unchanged number is evidence that an index was **not**
needed. The unique constraints from V6 (`course.slug`, `(course_id, position)`,
`(section_id, position)`) already serve the course page.

## 3. Finding 1 — Spring Data's keyset can't seek; one redundant predicate fixes it

ADR-0009 left this open: Spring Data writes "the rows after (key, id)" as an **OR chain**:

```sql
published_at < :k OR (published_at = :k AND id < :id)
```

Postgres can't use an `OR` as the **start** of an index range scan, so it walks the index from the
newest row and **filters** until it passes the cursor. Page 250 cost 2.35 ms (and grows with depth,
like OFFSET, only cheaper). The row comparison `(published_at, id) < (:k, :id)` seeks directly
(0.19 ms), but JPA's Criteria API can't express it.

The fix adds a predicate that **changes no result** but gives the planner a start condition:

```sql
published_at <= :k AND (published_at < :k OR (published_at = :k AND id < :id))
--         ↑ every row after the cursor already satisfies this → Index Cond: (published_at <= :k)
```

In code it's one more Specification leaf, `CourseSpecifications.keysetBound(sort, key)` (`<=` for
DESC sorts, `>=` for ASC), composed only on pages after the first:

```java
if (cursor != null) {
  CourseCursor after = CourseCursor.decode(cursor, sort);
  position = after.toScrollPosition();
  query = query.and(keysetBound(sort, after.key()));   // ⭐ lets Postgres SEEK to the cursor
}
```

**Result: 2.354 → 0.155 ms** at page 250, the same as the hand-written row comparison, while
keeping Spring Data's `Window` / `ScrollPosition` API. `CatalogIndexesIT` pins it: with the bound,
the plan has `Index Cond: (published_at <=`; without it, it doesn't.

## 4. Finding 2 — an expression index needs `ANALYZE`

`course_title_trgm_idx` is on `lower(title)`. Postgres keeps statistics for an **expression** only
after `ANALYZE` (0 rows in `pg_stats` for it right after `CREATE INDEX`). Without them the planner
guessed the selectivity of `lower(title) LIKE '%kube%'` and chose a bitmap scan + sort:
**1.38 ms**. After `ANALYZE` it correctly walks the NEWEST index and stops at 21 hits:
**0.68 ms**. `V8` therefore ends with `ANALYZE course;` (allowed inside Flyway's transaction,
unlike `VACUUM`).

## 5. What was shipped, and why each one

| Index (V8) | Size | Serves | Why this shape |
|---|---:|---|---|
| `course_published_newest_idx` `(published_at DESC, id DESC) WHERE status='PUBLISHED'` | 376 kB | Q1, Q2b, Q3, Q6 | NEWEST is the default list; ends in `id` like `CourseSort` (a total order) |
| `course_published_rating_idx` `(rating_average DESC, id DESC) WHERE …` | 376 kB | Q4 | |
| `course_published_price_idx` `(price_minor, id) WHERE …` | 376 kB | Q5 and Q5h | a btree reads both ways: one index for both price sorts |
| `course_instructor_updated_idx` `(instructor_id, updated_at DESC, id DESC)` | 592 kB | Q7 | not partial: an instructor sees every status. Leading on `instructor_id`, it also serves the FK, so deleting a user doesn't scan `course`. |
| `course_title_trgm_idx` `gin (lower(title) gin_trgm_ops)` | 536 kB | Q6x | `LIKE '%…%'` can't use a btree; rare and no-match searches no longer scan the table |

**Why partial (`WHERE status = 'PUBLISHED'`):** the public catalog never lists anything else, and
every Spring Data query for it carries `status = ?` with `PUBLISHED`, so the planner can use a
partial index. Measured against the non-partial `(status, published_at, id)`: **same speed**
(0.186 vs 0.144 ms page 1; 0.166 vs 0.178 ms page 250), **376 kB vs 584 kB** (35 % smaller). The
NestJS version had to reject partial indexes because Prisma can't declare them; Flyway runs plain
SQL, so here they ship.

## 6. Measured and rejected

| Candidate | Numbers | Why not |
|---|---|---|
| `(category_id, published_at DESC, id DESC) WHERE status='PUBLISHED'` | Q3 0.297 ms with it, **0.311 ms** without | **never chosen**: with 4 categories in an `IN` list the planner walks the NEWEST index and filters (each category is ~8 % of rows, so 21 hits arrive fast). Revisit if categories become skewed (a 0.1 % category would walk far). |
| non-partial `(status, published_at DESC, id DESC)` | same speed, 584 kB vs 376 kB | partial is smaller for the same plans (§5) |
| fixing `OFFSET` with an index | Q2o 15.7 ms with every index present | the cost is producing and discarding 4,980 rows, plus a disk sort at this width. That's ADR-0009's reason for keyset; no index helps. |
| `hibernate.default_batch_fetch_size` / more indexes for the course page | Q8 0.16–0.21 ms | already served by the unique constraints |
| an index on `course.category_id` alone (FK) | — | categories are reference data and are never deleted, so no FK check ever scans for them; noted here so a future "delete category" feature adds it |

## 7. Lessons

- **Measure the SQL the ORM really sends.** The OR-chain keyset looked fine on paper (and is
  "correct"); only the plan showed it filtering from the top.
- **A redundant predicate can be the whole optimisation.** It adds no meaning, only a start
  condition for the index scan.
- **Partial indexes fit "the list only shows X" perfectly**, provided the query repeats the
  partial condition literally (`status = 'PUBLISHED'`).
- **`ANALYZE` after creating an expression index.** Otherwise the planner has no statistics for
  the expression.
- **Record the losers.** A rejected index with its numbers is more convincing than five that all
  "helped".
- **Small tables lie in tests.** On a 10-row test table a seq scan *is* the best plan, so
  `CatalogIndexesIT` uses `SET LOCAL enable_seqscan = off` to check that the indexes **can**
  serve each query and that the cursor is an `Index Cond`.

## 8. 60-second recall

- 10,000 courses, every list query `EXPLAIN ANALYZE`d before and after: **8.5 → 0.14 ms**
  (default list), 9.4 → 0.19 (rating), 9.0 → 0.18 (price), 2.4 → 0.16 (instructor), no Sort node
  anywhere.
- **5 indexes**, 4 of them **partial** (`WHERE status='PUBLISHED'`, 35 % smaller, same speed),
  each ending in `id` to match the total order; one GIN trigram for `LIKE '%…%'`.
- **Spring Data's keyset OR chain can't seek** (page 250: 2.35 ms). Adding
  `published_at <= :key` gives the planner a start condition: **0.155 ms**, equal to a row
  comparison, without leaving Spring Data.
- **OFFSET page 250 stays at 15.7 ms** whatever you index: the reason for keyset.
- **Expression index + no `ANALYZE` = a wrong plan** (1.38 vs 0.68 ms): V8 analyzes.
- Rejected with numbers: the category index (never chosen), the non-partial composite (bigger).
