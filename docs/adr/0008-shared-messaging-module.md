# ADR-0008 — A shared `messaging` module owns the outbox protocol

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai
**Context links:** [ADR-0005](0005-hand-rolled-outbox-over-modulith-registry.md) (the outbox itself) · [`docs/lld/notification.md`](../lld/notification.md) · [`docs/lld/platform-kernel.md`](../lld/platform-kernel.md)

## Context

Phase 2 built the transactional outbox inside the api's `platform` module: the writer, the
`FOR UPDATE SKIP LOCKED` claim, the lease, backoff and the relay. Phase 4 moves **delivery** to
the worker (ADR-0001: the api never waits on third parties), while the api keeps **writing** events
in its transactions.

So two deployables now speak one protocol over one table. The claim SQL, the status transitions
and the lease semantics must be **identical** on both sides. A divergence (say, a relay that
ignores the lease) causes duplicate or lost deliveries.

## Decision

A new Maven module, **`backend/messaging`**: a Spring-aware library (not an app) that both
`api` and `worker` depend on. It owns:

- the public API: `OutboxMessage`, `OutboxHandler`, `OutboxWriter`;
- `JdbcOutboxWriter` (append in the caller's transaction), `OutboxRepository` /
  `JdbcOutboxRepository` (claim, mark, reschedule) and `OutboxRelay` (+ scheduler);
- `OutboxProperties` (`masternova.outbox.*`);
- the table's migration: `db/migration/V2__platform_outbox.sql` moves here unchanged. Flyway
  tracks script names and checksums, not paths, so applied history stays valid;
- **auto-configuration** (`MessagingAutoConfiguration`, registered in
  `META-INF/spring/…AutoConfiguration.imports`). The writer is always on. The relay runs only
  where `masternova.outbox.relay-enabled=true`: **the worker**. The api sets it `false`.

`kernel` stays plain Java (no Spring). `messaging` is the Spring layer on top of it.

## Consequences

- **Positive:**
  - One implementation of the protocol, tested once (its ITs move with it).
  - Adding a consumer is one `OutboxHandler` bean in the worker.
  - The api can't accidentally consume events: no relay runs there.
  - It shows how Spring Boot starters work (auto-configuration + conditions).
- **Negative:**
  - A third backend module to version.
  - The relay marks events with **no handler in its process** as DONE ("unhandled"). That's
    correct only while **exactly one** process relays. Running a relay in the api again would
    swallow the worker's events. The property default is `false`, so relaying is opt-in, and the
    LLD says so.
  - Both apps must agree on `DomainEvent` JSON shapes. Consumers deserialize into their **own**
    view records (the worker's `UserRegisteredView`), so the producer can add fields freely.

## Alternatives rejected

| Option | Why not |
|---|---|
| copy the relay into the worker | two copies of a concurrency protocol drift |
| put it in `kernel` | the kernel is deliberately Spring-free; the relay needs `JdbcClient`, transactions and scheduling |
| the worker calls the api over HTTP to fetch events | an extra network hop and failure mode, for data both already reach through the same database |
| a message broker (Kafka/RabbitMQ) | infrastructure we don't need at this scale. The outbox already gives durable, ordered-enough, at-least-once delivery (ADR-0005). |
