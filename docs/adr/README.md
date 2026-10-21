# Architecture Decision Records

**One ADR = one decision.** Numbered and never deleted. A superseded ADR is marked
`Status: superseded by NNNN`, because the trail of changed decisions is itself the signal.

Write one whenever a real alternative existed. If there was no alternative, it wasn't a
decision and doesn't need a record. Domain decisions already argued in the NestJS Masternova
(outbox, refresh rotation, keyset paging, optimistic concurrency, entitlement cache, webhook
dedupe) get a short Java-flavoured ADR in the phase that implements them, so the reasoning is
re-derived rather than copied.

| # | Decision | Status |
|---|---|---|
| [0001](0001-spring-modulith-modular-monolith.md) | Spring Modulith modular monolith + separate worker | accepted |
| [0002](0002-maven-multi-module.md) | Maven multi-module build over Gradle | accepted |
| [0003](0003-vertical-slices.md) | Build in vertical slices (backend + Angular per module) | accepted |
| [0004](0004-kubernetes-first-deploy.md) | Local Kubernetes + GitOps first, AWS last and optional | accepted (§2 amended by 0020) |
| [0005](0005-hand-rolled-outbox-over-modulith-registry.md) | Hand-rolled transactional outbox over Spring Modulith's event publication registry | accepted |
| [0006](0006-rotating-refresh-tokens-over-stateless-jwt.md) | Short-lived JWT + rotating refresh tokens with reuse detection | accepted |
| [0007](0007-alpine-jre-runtime-image.md) | Alpine Temurin JRE as the runtime base for the Spring Boot images | accepted |
| [0008](0008-shared-messaging-module.md) | A shared `messaging` module owns the outbox protocol (writer in the api, relay in the worker) | accepted |
| [0009](0009-keyset-pagination-over-offset.md) | Keyset (cursor) pagination over `LIMIT/OFFSET`, via Spring Data `Window` + our own typed cursor | accepted |
| [0010](0010-optimistic-concurrency-with-version.md) | Optimistic concurrency: JPA `@Version` + `expectedVersion` in the body; the root's version covers the aggregate | accepted |
| [0011](0011-undo-with-stored-inverses.md) | Curriculum undo: stored inverse commands (9–39× smaller per edit than a snapshot stack, intent kept), Memento only for what a removal destroys | accepted |
| [0012](0012-seaweedfs-as-local-s3.md) | SeaweedFS as the local S3 server (MinIO's images stopped being published); 4 candidates probed, the only one refusing foreign-origin CORS like AWS *and* mature | accepted |
| [0013](0013-hls-for-playback.md) | HLS with fMP4 segments, aligned-GOP H.264/AAC ladder, presigned segments behind a playback ticket | accepted |
| [0014](0014-upload-progress-from-the-provider.md) | Upload progress lives in the provider: resume and complete ask `ListParts`; the client never sends ETags | accepted |
| [0015](0015-tracing-with-micrometer-observation-and-otlp.md) | Tracing through Micrometer Observation + the OTel SDK (not the Java agent); the outbox row carries the W3C trace context in `headers` | accepted |
| [0016](0016-cache-the-entitlement-row-not-the-decision.md) | Cache the entitlement row, not the decision: a Decorator over the repository, evicted after commit, negatives cached, failing open to Postgres | accepted |
| [0017](0017-short-ip-bound-playback-token.md) | Learner playback: a 5-minute token bound to user, lecture and IP (signed, not carried); the player re-tickets on a 403 | accepted |
| [0018](0018-claim-webhook-events-before-processing.md) | Webhook dedupe: claim the provider's event id (primary key) in the same transaction as the effects; a failed processing rolls the claim back so the redelivery runs | accepted |
| [0019](0019-grant-access-in-the-order-transaction.md) | Grant course access in the transaction that marks the order paid (and revoke in the refund's); only receipts and enrollment stay eventual | accepted |
| [0020](0020-gitops-human-committed-tag-app-of-apps.md) | GitOps by a human-committed image tag (checked on GHCR first); two charts (infra, apps) under an Argo CD app-of-apps with health-gated sync waves | accepted |
| [0021](0021-modulith-registry-for-in-process-module-listeners.md) | Spring Modulith's JDBC event publication registry, only for `@ApplicationModuleListener`s (enrollment); recovered by a scheduled resubmitter on one replica, not on restart; amends 0005 and 0019 §4 | accepted |
| [0022](0022-progress-write-back-buffer.md) | Lecture progress through a Redis write-back buffer (Lua merge, `RENAME` swap), flushed every 30 s by one replica; the merge is a semilattice so a crashed flush is replayed; ≤ 30 s accepted loss | accepted |
| [0023](0023-exact-rating-tally-pushed-in-the-review-transaction.md) | Course ratings: an exact per-star tally locked first by every review write, pushed to catalog's columns in the same transaction (not an event), recounted nightly under the same lock | accepted |
| [0024](0024-search-in-postgres-fts-pgvector-rrf.md) | Search in Postgres: a generated `tsvector` + pgvector embeddings (Spring AI's Ollama client, not `PgVectorStore`) kept fresh by a sweeper; three rankers, hybrid by RRF, failing open to keywords | accepted |
