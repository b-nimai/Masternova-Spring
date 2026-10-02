# ADR-0001 — Spring Modulith modular monolith + a separate worker

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai

## Context

Masternova has about eight bounded contexts (identity, catalog, media, commerce, enrollment,
engagement, notification, analytics) and two different resource profiles. HTTP handling is
I/O-bound and steady. ffmpeg transcoding is CPU-bound and bursty. The NestJS version already
chose a modular monolith (its ADR-0001), so the question here is how to *enforce* module
boundaries in Java.

## Decision

- **One `api` deployable.** Each bounded context is a direct sub-package of
  `com.masternova.api`, which makes it a Spring Modulith *application module*. Only a
  module's top-level public types are its API; sub-packages are internal.
- **`ModularityTests` fails the build** on a cross-module internal reference or a dependency
  cycle. It's the Java equivalent of the NestJS ESLint import-boundary rule, and it's on from
  commit one.
- **Cross-context effects that can fail independently** go through domain events and a
  transactional outbox (Phase 2).
- **A separate `worker` deployable** exists because of the resource profile, not the domain.
  It scales on queue depth; the api scales on RPS.
- **A `kernel` library** holds only what both deployables agree on: event contracts and
  `@DesignPattern`. It has no Spring dependency.

## Consequences

- **Positive:**
  - One Postgres transaction covers what must be atomic, so there's no saga.
  - One deploy, one migration path.
  - Modulith also generates module diagrams (`target/spring-modulith-docs`) and offers
    `@ApplicationModuleTest` for testing one module in isolation.
- **Negative:**
  - One process means one blast radius for a memory leak.
  - Scaling is coarse on the api side.
- **Mitigation:** module boundaries are verified in CI, so splitting a module out later (media
  first, then commerce) is a move, not a rewrite.

## Alternatives rejected

| Option | Why not |
|---|---|
| Microservices | Distributed transactions and N deploy pipelines in exchange for nothing at this traffic |
| Maven module per bounded context | Stronger compile-time walls, but 8+ poms of ceremony. Modulith gives the same guarantee with a test. |
| ArchUnit rules only | Modulith is built on ArchUnit and adds module-aware rules, docs and event support for free |
