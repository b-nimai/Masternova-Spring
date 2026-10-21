-- Semantic search: one embedding per published course (Phase 11.4). docs/lld/search.md §3, §5,
-- ADR-0024. The vector extension exists since V1.

CREATE TABLE course_embedding (
    course_id    UUID         PRIMARY KEY REFERENCES course (id) ON DELETE CASCADE,
    -- ⭐ what made it, and of which text: the sweeper re-embeds a course when either no longer
    --    matches (an edit, a model change), so no event is needed to keep it fresh
    model        VARCHAR(100) NOT NULL,
    content_hash CHAR(32)     NOT NULL,   -- md5 of the embedded text, computed in SQL
    -- the dimension IS the model's (nomic-embed-text: 768); another model = another migration
    embedding    vector(768)  NOT NULL,
    embedded_at  TIMESTAMPTZ  NOT NULL
);

-- ⭐ HNSW: an approximate nearest-neighbour graph. Cosine distance (<=>), because the model's
--    vectors are compared by direction, not length. pgvector's defaults (m = 16,
--    ef_construction = 64). Only published courses have a row (the sweeper deletes the others),
--    so the graph holds only what search can return.
CREATE INDEX course_embedding_hnsw_idx
    ON course_embedding USING hnsw (embedding vector_cosine_ops);

-- ⭐ Vacuum this table early. Every re-embedding is an UPDATE (a dead row + a new graph entry),
--    and HNSW keeps dead entries in the graph until VACUUM: measured on pgvector 0.8, rows added
--    to a graph of mostly dead entries were unreachable from it — a search returned 0 of 2 until
--    VACUUM (docs/db/indexes.md §9). The default (20 % of the table dead) is too lax for a table
--    that is rewritten whenever the catalog is edited; 2 % + 50 rows keeps the graph clean.
ALTER TABLE course_embedding SET (autovacuum_vacuum_scale_factor = 0.02,
                                  autovacuum_vacuum_threshold = 50);
