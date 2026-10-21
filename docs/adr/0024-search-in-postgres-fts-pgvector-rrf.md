# ADR-0024 — Course search in Postgres: a generated `tsvector`, pgvector embeddings kept fresh by a sweeper, hybrid by Reciprocal Rank Fusion, failing open to keywords

**Status:** accepted · **Date:** 2026-10-21 · **Deciders:** Nimai
**Context links:** [`docs/lld/search.md`](../lld/search.md) · [`catalog.md`](../lld/catalog.md) · [ADR-0004](0004-kubernetes-first-deploy.md) (what runs in the cluster)

## Context

Phase 11 replaces "title contains" with ranked search that understands stemming and meaning
(search LLD §1–§2). Meaning needs an embedding model: an external, slow, sometimes-absent
system. The catalog is ~10,000 courses at the scale this project measures. The data, its
visibility rule (published only) and its backups are already in Postgres, which ships with
`pgvector` in every image we run (compose and Testcontainers: `pgvector/pgvector:pg17`; the
cluster: CNPG `17-standard`, extension created at init).

## Decision

1. **Everything stays in Postgres.** Keywords: a **generated `STORED` `tsvector`** on `course`
   (title A, subtitle B, description C; `english` for English courses, `simple` otherwise) with a
   partial GIN index. Meaning: a `course_embedding` table (`vector(768)`, `model`, `content_hash`)
   with an HNSW cosine index, joined to `course` for the visibility rule. No search engine, no
   vector database.
2. **Spring AI for the model client only.** `spring-ai-ollama`'s `OllamaEmbeddingModel` behind
   Spring AI's `EmbeddingModel` interface, built in our own `@Configuration` (no starter: the
   starter also brings a chat client, chat memory and their auto-configuration). Not
   `PgVectorStore`: it owns its own table outside Flyway and `ddl-auto=validate`, and "published
   only" would become a metadata filter instead of a join.
3. **The model:** `nomic-embed-text` (768 dimensions, 274 MB, Apache-2.0) on **Ollama**, in a
   compose profile (`make ai`). Another provider is a dependency + an `EmbeddingModel` bean;
   the dimension is the column type, so a model with a different one is a migration.
4. **A sweeper, not events, keeps embeddings fresh:** every 60 s, one replica, up to 32 published
   courses whose embedding is missing, made by another model, or of different text (md5 computed
   in SQL); one batch call to the model outside any transaction; unpublished courses' vectors
   deleted. Freshness ≤ 60 s.
5. **Three rankers behind one Strategy (`CourseRanker`)**, chosen per request: `KEYWORD`
   (`ts_rank_cd`), `SEMANTIC` (cosine distance, cut off at `max-distance`), `HYBRID` (default):
   **Reciprocal Rank Fusion**, `Σ 1/(60 + rank)`, over both lists.
6. **Fail open to keywords.** The query embedding has a 2 s timeout and a 30 s breaker; when the
   model is unavailable, semantic and hybrid answer the keyword ranking with `degraded: true`.
   Search never fails because the model did. **Semantic search is off unless configured**
   (`masternova.search.embedding.enabled`, on in compose's `ai` profile and `make api`): the
   cluster and CI run keyword-only until a model is deployed there.
7. **No pagination of results** (top `limit` ≤ 50). Relevance is a per-query float; the filtered
   catalog keeps its keyset pages.

## Consequences

- One datastore, one backup, one visibility rule; the GIN and HNSW indexes cost write time on
  `course` only when the text changes (the vector is generated, the embedding is a separate row).
- Adding the generated column rewrites `course` once under an `ACCESS EXCLUSIVE` lock (measured in
  the search LLD §9). Acceptable at this size; a large table would get a trigger + batched backfill.
- The api now depends on Spring AI 2.0 (`spring-ai-ollama` and through it `spring-ai-model`,
  `spring-webflux` and `reactor-core`; it uses Jackson 3 like the rest of the app). Servlet MVC
  stays the web stack: Boot picks it when both are present.
- Two pgvector behaviours shape the query (docs/db/indexes.md §9): `hnsw.ef_search` caps the rows
  a scan returns (100 on every connection), and dead graph entries hide live rows until `VACUUM`
  (V28 vacuums the table early; `hnsw.iterative_scan` is on for every connection).
- The cluster has no Ollama, so its search is keyword-only. Deploying a model there (D6 or later)
  is a chart change, not a code change.
- A new embedding model re-embeds the catalog at 32 courses per minute (10,000 courses ≈ 5 h);
  meanwhile semantic results compare vectors from two models. Acceptable for a model change, which
  is rare and deliberate.

## Alternatives considered

- **Elasticsearch / OpenSearch, or a vector database:** a second store fed by events, with its own
  outage and a copy of the visibility rule. Postgres answers this catalog's queries in
  milliseconds.
- **Spring AI `PgVectorStore`:** see Decision §2.
- **Embedding on a `CoursePublished` event:** a model outage loses the work until a replay; the
  sweeper heals outage, edit and model change with one mechanism.
- **In-process ONNX embeddings (Spring AI Transformers):** no extra service, but ~100 MB of native
  runtime per platform in the api image (amd64 + arm64), a model download at startup, and the AOT
  training run of the Dockerfile would need it too.
- **A weighted sum of normalised scores:** the scales of `ts_rank_cd` and cosine distance are
  query-dependent; RRF needs no weights.
