# ADR-0005 — A hand-rolled transactional outbox over Spring Modulith's event publication registry

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai
**Context links:** [`docs/lld/platform-kernel.md`](../lld/platform-kernel.md) · [pattern 17](../../patterns/docs/17-transactional-outbox.md) · ADR-0001 (Modulith monolith + worker)

## Context

Domain events must reach reactions that live **in another process** (the worker: email,
transcoding, search indexing) and must never be lost or invented (the dual-write problem).
Spring Modulith, already on our classpath for module verification, ships an **Event
Publication Registry**:

- When an event is published and a transactional listener exists, Modulith writes an
  `event_publication` row **per listener**, in the publishing transaction.
- `@ApplicationModuleListener` (async + `REQUIRES_NEW` + after-commit) runs the listener, and the
  row is marked complete when it succeeds.
- Incomplete publications can be resubmitted on restart, or through the
  `IncompleteEventPublications` API.
- **Externalization** (`@Externalized`) can forward events to Kafka, AMQP, JMS or SQS through the
  same registry.

The question: build our own outbox (task 2.4), or use the registry?

## Decision

**Keep the hand-rolled outbox** (`outbox_message` + `OutboxRelay`) as the single mechanism for
**durable** delivery. Use plain `@TransactionalEventListener` for **non-critical in-process**
reactions. Do not add a Modulith persistence starter for now.

## Why

| Need | Hand-rolled outbox | Modulith registry |
|---|---|---|
| The **worker** (a different deployable) claims and processes events | ✅ the relay is just SQL: any process with the table can claim with `FOR UPDATE SKIP LOCKED` | ❌ it tracks **in-process** listeners. A separate process would need externalization to a broker. |
| No new infrastructure (no Kafka/RabbitMQ in Phase 2) | ✅ Postgres only | externalization needs a broker |
| Per-message attempts, exponential backoff, a DEAD state, an inspectable `last_error` | ✅ explicit columns we designed (V2) | incomplete/failed tracking + resubmission; backoff and dead-lettering policy are ours to build around it |
| Multiple relay instances without double processing | ✅ `SKIP LOCKED` + lease, proven by a 4-relay IT | resubmission must be coordinated (e.g. on one node) |
| Learning value: understand the pattern itself | ✅ every line is ours and documented | hides the mechanism |
| Less code to maintain | ❌ ~300 lines + tests | ✅ |

The deciding factor is **cross-process consumption without a broker**. The registry is excellent
for making in-process module listeners reliable, but our reliable consumers live in the worker.

## Consequences

- **Positive:**
  - One durable mechanism, visible in SQL and fully tested (`TransactionalOutboxIT`).
  - Works unchanged when the relay moves to the worker in Phase 4.
  - A broker can be added *behind* the relay later (the relay publishes instead of calling
    handlers) without changing a single publisher.
- **Negative:**
  - We own retention (a cleanup job for `DONE` rows, Phase 4/D5), metrics (D3) and replay tooling.
  - Consumers must be idempotent (at-least-once), whichever option we chose.
- **Revisit when:**
  - In-process `@ApplicationModuleListener`s appear whose work must survive a crash. Then adding
    `spring-modulith-starter-jdbc` for *those* listeners is cheap, and the two mechanisms coexist
    (different tables, different jobs).
  - Or we adopt a broker. Then Modulith externalization becomes attractive.

## Alternatives rejected

| Option | Why not |
|---|---|
| Modulith registry + externalization to Kafka | adds a broker to run, secure and monitor before we need one |
| Modulith registry only, worker reads `event_publication` | couples the worker to Modulith's internal table format, which is not a public contract |
| Debezium CDC on the outbox table | Kafka Connect infrastructure; polling is enough at our scale (D5 measures it) |
