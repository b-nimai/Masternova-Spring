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
| [0004](0004-kubernetes-first-deploy.md) | Local Kubernetes + GitOps first, AWS last and optional | accepted |
| [0005](0005-hand-rolled-outbox-over-modulith-registry.md) | Hand-rolled transactional outbox over Spring Modulith's event publication registry | accepted |
| [0006](0006-rotating-refresh-tokens-over-stateless-jwt.md) | Short-lived JWT + rotating refresh tokens with reuse detection | accepted |
| [0007](0007-alpine-jre-runtime-image.md) | Alpine Temurin JRE as the runtime base for the Spring Boot images | accepted |
| [0008](0008-shared-messaging-module.md) | A shared `messaging` module owns the outbox protocol (writer in the api, relay in the worker) | accepted |
| [0009](0009-keyset-pagination-over-offset.md) | Keyset (cursor) pagination over `LIMIT/OFFSET`, via Spring Data `Window` + our own typed cursor | accepted |
