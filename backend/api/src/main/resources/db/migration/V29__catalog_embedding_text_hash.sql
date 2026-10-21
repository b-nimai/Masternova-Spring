-- The embedding sweep compares hashes, not text (Phase 11 review). docs/lld/search.md §5, §9.
-- Before: every run rebuilt title + subtitle + description for EVERY published course and hashed
-- it (24 ms per run on 10,000 seeded courses, ~80 MB hashed a minute with real descriptions), to
-- find a handful of stale ones.

-- ⭐ The ONE definition of "the text a course is embedded from": the generated hash below and the
--    sweep's text both call it, so they can't disagree. IMMUTABLE is what lets a generated column
--    use it. ⚠ Changing the function later does NOT recompute stored hashes: a new definition is a
--    new function (and column) in a new migration.
CREATE FUNCTION catalog_embedding_text(title text, subtitle text, description text)
    RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN left(title || E'\n' || coalesce(subtitle, '') || E'\n' || description, 8000);

-- Computed once per write of the row, not once per sweep. Rewrites `course` once (like V27).
ALTER TABLE course ADD COLUMN embedding_text_hash CHAR(32) GENERATED ALWAYS AS (
    md5(catalog_embedding_text(title, subtitle, description))
) STORED;
