-- Catalog indexes (Phase 5.9). Every one was measured with EXPLAIN ANALYZE on 10,000 seeded courses
-- before it was added, and every rejected candidate is recorded with its numbers:
-- docs/db/indexes.md. (V6 deliberately had none, so "before" is a real point in this history.)

-- ⭐ PARTIAL indexes: the public catalog only ever lists PUBLISHED courses, so drafts and archived
--    courses stay out of the index. Same speed as (status, …) composites, about a third smaller.
--    Each ends in id, matching CourseSort's total order, so a page is an index walk with no Sort.

-- NEWEST (the default list), its keyset pages, and the category browse (measured: the planner walks
-- this index and filters by category; a dedicated category index was never chosen).
CREATE INDEX course_published_newest_idx
    ON course (published_at DESC, id DESC) WHERE status = 'PUBLISHED';

-- HIGHEST_RATED
CREATE INDEX course_published_rating_idx
    ON course (rating_average DESC, id DESC) WHERE status = 'PUBLISHED';

-- PRICE_LOW reads it forwards, PRICE_HIGH backwards: one index for both directions
CREATE INDEX course_published_price_idx
    ON course (price_minor, id) WHERE status = 'PUBLISHED';

-- The instructor's own list (every status). Leading on instructor_id, it also serves the foreign
-- key: deleting a user no longer scans course.
CREATE INDEX course_instructor_updated_idx
    ON course (instructor_id, updated_at DESC, id DESC);

-- Title search: lower(title) LIKE '%…%' can't use a btree. A trigram GIN index answers rare and
-- no-match searches without scanning the table. (pg_trgm is a trusted extension: the database
-- owner may create it.)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX course_title_trgm_idx
    ON course USING gin (lower(title) gin_trgm_ops);

-- ⭐ An EXPRESSION index (lower(title)) gets statistics only from ANALYZE. Without them the planner
--    guessed the selectivity of every title search and chose a slower plan for common words
--    (measured: 1.38 ms → 0.75 ms once analyzed). Autovacuum would get there eventually; a
--    migration that creates one shouldn't leave it to chance. (ANALYZE, unlike VACUUM, may run
--    inside Flyway's transaction.)
ANALYZE course;
