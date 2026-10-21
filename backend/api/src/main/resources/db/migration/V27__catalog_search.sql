-- Catalog full-text search (Phase 11.3). docs/lld/search.md §3, §9, ADR-0024.

-- ⭐ A GENERATED column: Postgres computes it from the row on every insert/update, so it can never
--    disagree with the text it indexes (no trigger, no application code to forget).
--    Weights rank a title hit (A) over a subtitle hit (B) over a description hit (C).
--    English courses are stemmed ("deploying" = "deploy"); other languages use 'simple' (lowercase,
--    no stemming): the English stemmer would mangle Hindi. Queries search both (search LLD §5).
--    ⚠ ADD COLUMN … STORED rewrites the table under an ACCESS EXCLUSIVE lock: measured once on
--    10,000 courses (search LLD §9). A large production table would get a plain column, a trigger
--    and a batched backfill instead.
ALTER TABLE course ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector(CASE WHEN language = 'en' THEN 'english'::regconfig
                               ELSE 'simple'::regconfig END, title), 'A') ||
    setweight(to_tsvector(CASE WHEN language = 'en' THEN 'english'::regconfig
                               ELSE 'simple'::regconfig END, coalesce(subtitle, '')), 'B') ||
    setweight(to_tsvector(CASE WHEN language = 'en' THEN 'english'::regconfig
                               ELSE 'simple'::regconfig END, description), 'C')
) STORED;

-- GIN answers "which rows contain these lexemes". Partial: search only ever returns PUBLISHED.
CREATE INDEX course_search_idx ON course USING gin (search_vector) WHERE status = 'PUBLISHED';
