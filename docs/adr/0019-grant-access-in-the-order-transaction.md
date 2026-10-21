# ADR-0019 — Grant course access in the same transaction that marks the order paid

**Status:** accepted · **Date:** 2026-10-14 · **Deciders:** Nimai
**Amended by:** [ADR-0021](0021-modulith-registry-for-in-process-module-listeners.md) (2026-10-15): §4's enrollment listens to `EntitlementGranted`/`EntitlementRevoked` in-process (durable through Modulith's registry), not to `OrderPaid` through the outbox.
**Context links:** [`docs/lld/order-state-machine.md`](../lld/order-state-machine.md) §3, §5 · [ADR-0005](0005-hand-rolled-outbox-over-modulith-registry.md) (outbox) · [`entitlement-engine.md`](../lld/entitlement-engine.md)

## Context

When an order becomes `PAID`, the buyer must get every course in it. Commerce owns the order;
entitlement owns the access row. Two modules, two tables, one database.

The usual modular-monolith answer is an event: commerce commits `PAID` and publishes `OrderPaid`;
entitlement listens and grants. That keeps the modules decoupled, but between the two commits
the learner has paid and has no course. If the listener fails (a bug, a constraint, a deploy),
the outbox retries it, and until then support gets the ticket "I paid and it says buy now".

## Decision

1. `PaymentEvents.apply` runs in one transaction: lock the order, move it to `PAID`, call
   `EntitlementApi.grant(user, course, PURCHASE)` for each line (the call **joins** the
   transaction), count the coupon redemption, publish `OrderPaid` to the outbox.
2. A refund does the same in reverse: `REFUNDED` + `EntitlementApi.revoke` per line +
   `OrderRefunded`, one transaction.
3. **Either all of it commits or none of it does.** An order is never `PAID` without access, and
   access never exists for an order that isn't.
4. What can be late stays an event: the receipt email (worker), enrollment rows and progress
   (Phase 10). They read `OrderPaid` from the outbox.
5. The entitlement cache is evicted **after** the commit (ADR-0016), so the first page the learner
   loads after paying sees the new row.

## Alternatives rejected

| Option | Why not |
|---|---|
| **Grant from an `OrderPaid` listener (outbox → worker → api)** | Eventual access for something the learner just paid for: a window where they have paid and are locked out, and a failure mode ("paid, no course") that needs a dead-letter runbook. The decoupling buys nothing: both modules live in one process and one database. |
| **Grant from an in-process `@TransactionalEventListener(AFTER_COMMIT)`** | Runs after the commit, outside it: a crash or exception between the two loses the grant with nothing to retry it. |
| **`@ApplicationModuleListener` (Modulith's async listener + event registry)** | Same eventual window, plus the registry table we chose not to run (ADR-0005). |
| **Two-phase anything / saga** | One database: a local transaction is the strongest and cheapest tool there is. |

## Consequences

- Commerce depends on `EntitlementApi` (a public module API; `ModularityTests` keeps it at
  that). Entitlement does not depend on commerce: no cycle.
- The order transaction is a little longer: one upsert per course in the order (at most
  `cart-max-items`, 20).
- A grant that fails (a course deleted between checkout and payment) fails the whole transition:
  the webhook gets a 500 and is redelivered, and the order stays visibly unpaid, which is the
  honest state. The runbook for a stuck order is to look at the webhook's error.
