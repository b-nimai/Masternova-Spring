-- Baseline: extensions later migrations rely on. Tables arrive with their modules.
CREATE EXTENSION IF NOT EXISTS citext; -- case-insensitive emails (Phase 3, identity)
CREATE EXTENSION IF NOT EXISTS vector; -- pgvector embeddings (Phase 11, semantic search)
