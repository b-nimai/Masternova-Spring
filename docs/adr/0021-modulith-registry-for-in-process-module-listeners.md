# ADR-0021 — Spring Modulith's JDBC event publication registry for in-process module listeners

**Status:** accepted · **Date:** 2026-10-15 · **Deciders:** Nimai
**Amends:** [ADR-0005](0005-hand-rolled-outbox-over-modulith-registry.md) (its "revisit when" has
happened) · [ADR-0019](0019-grant-access-in-the-order-transaction.md) §4 (enrollment listens to
entitlement in-process, not to `OrderPaid` through the outbox)
**Context links:** [`docs/lld/progress.md`](../lld/progress.md) §5 · [pattern 07](../../patterns/docs/07-observer.md)

## Context

Phase 10 adds the `enrollment` module. An enrollment (the row behind "My Learning" and progress
rollups) must exist whenever a learner holds a course: after a purchase, a free enrollment or an
admin grant, and it must end when the access is revoked.

- The trigger is **entitlement's** fact (`EntitlementGranted` / `EntitlementRevoked`, published
  only when the row changed), not commerce's `OrderPaid`: only entitlement events cover all three
  sources, and listening to both would enroll a buyer twice.
- The work happens **in the api** (the `enrollment` table and catalog's `enrollment_count` are api
  data). The outbox delivers to the **worker** (ADR-0008), so it isn't the path.
- It may be **late** (ADR-0019 keeps access itself synchronous) but must **never be lost**. A plain
  `@TransactionalEventListener(AFTER_COMMIT)` is lost when the process dies between the commit and
  the listener, or when the listener throws.
- It must not be able to **fail the payment**: an in-transaction `@EventListener` would roll a paid
  order back because of a bug in a dashboard table.

ADR-0005 named this case: "in-process `@ApplicationModuleListener`s whose work must survive a crash.
Then adding `spring-modulith-starter-jdbc` for *those* listeners is cheap, and the two mechanisms
coexist."

## Decision

1. Add `spring-modulith-starter-jdbc`. Flyway owns its table (`V22`: Modulith 2.x's v2 schema
   copied from the jar; `schema-initialization` stays off, Hibernate stays `validate`).
2. **Only `@ApplicationModuleListener`s are registered**
   (`spring.modulith.events.registry-trigger-annotation`). Existing best-effort
   `@TransactionalEventListener`s (the dev verification-link logger) stay best-effort and write no
   rows.
3. `completion-mode: delete`: a finished publication is deleted, so the table holds only work in
   flight or failed.
4. **Recovery is scheduled, not on restart.** `republish-outstanding-events-on-restart` would make
   every api replica replay everything at every rollout, concurrently with the replicas still
   running it. Instead:
   - staleness: a publication still `PUBLISHED` after 2 min (the process died before the async
     listener started) or `PROCESSING` after 5 min (it died mid-listener) is marked `FAILED`;
   - `FailedPublicationResubmitter` (platform, `@Scheduled` every minute under ShedLock, so one
     replica) resubmits `FAILED` publications older than 1 minute, 100 at a time, and stops after
     10 attempts (a poison event stays visible, counted by `events.publications.failed`).
5. Listeners stay **idempotent and order-independent**: delivery is at-least-once and two events
   for one pair can run concurrently or in either order. Each event carries its own time
   (`EntitlementGranted.grantedAt`, added; `EntitlementRevoked.revokedAt`), and the enrollment
   applies an event only if it is newer than the row's `status_at`. A revoke that arrives before
   its grant leaves a `REVOKED` tombstone, so the late grant can't resurrect it.

## Why both mechanisms

| | Outbox (ADR-0005/0008) | Modulith registry (this ADR) |
|---|---|---|
| Consumer runs in | the **worker** | the **api**, in the publishing module's process |
| Claimed by | `FOR UPDATE SKIP LOCKED` over N workers | the publishing JVM's async executor; resubmission on one replica |
| Retries / dead state | ours: attempts, backoff, `DEAD` | Modulith's `FAILED` + our resubmitter, 10 attempts |
| Used for | email, transcoding, anything leaving the process | reactions of one module to another's fact inside the api |

One event can travel both ways: `EntitlementGranted` goes to the outbox (any worker consumer) *and*
to the registry for the enrollment listener. Different tables, different jobs, as ADR-0005 predicted.

## Consequences

- **Positive:**
  - A crash, a deploy or a bug in enrollment can't cost a learner their dashboard row: the
    publication is in the same transaction as the grant, and it stays until a listener succeeds.
  - Commerce and entitlement don't know enrollment exists; enrollment is a pure Observer.
  - The enrollment listener failing never rolls a payment back.
- **Negative:**
  - Two event mechanisms to explain. The rule is in the table above: leaves the api → outbox.
  - A second table on the write path of every grant and revoke (one `INSERT`, one `DELETE`).
  - We depend on Modulith's table format for operations (reading `event_publication` in an
    incident). The schema is versioned by Modulith (`v2`), and the migration pins the one we run.
- **Revisit when:** a broker arrives (externalize the same events), or Modulith's resubmission
  gains coordination across instances (then drop ShedLock from the resubmitter).

## Alternatives rejected

| Option | Why not |
|---|---|
| **`@EventListener` inside the grant's transaction** | Atomic, but enrollment's bugs would fail payments, and a slow listener holds the order's row lock. |
| **Plain `@TransactionalEventListener(AFTER_COMMIT)`** | Lost on a crash after commit or on an exception; nothing retries it. |
| **The outbox, consumed by the worker** | The worker would write the api's `enrollment` table and catalog's counter: two deployables owning one module's data. |
| **The outbox, with a relay inside the api for api-side handlers** | The api's relay would also claim the worker's messages (ADR-0008 keeps `relay-enabled: false` for that reason); per-handler routing is the registry, rebuilt. |
| **Enrollment from `OrderPaid`** | Misses free enrollments and admin grants; with both, a purchase enrolls twice. |
| **No enrollment table: derive "My Learning" from entitlement rows** | The dashboard needs progress rollups (completed lectures, last lecture, last activity) next to the access, and a revoked learner's progress must survive a re-purchase. |
| **`republish-outstanding-events-on-restart: true`** | Every replica replays everything at every rollout, while the old pods may still be running the same publications. |
