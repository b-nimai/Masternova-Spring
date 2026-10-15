# ADR-0018 — Claim a webhook's event id before processing it, in the same transaction

**Status:** accepted · **Date:** 2026-10-14 · **Deciders:** Nimai
**Context links:** [`docs/lld/order-state-machine.md`](../lld/order-state-machine.md) §5, §8 · [`entitlement-engine.md`](../lld/entitlement-engine.md) §8 (a late grant after a refund)

## Context

Razorpay delivers webhooks **at least once**. It retries a delivery that didn't get a 2xx for up
to 24 hours, it may send the same event more than once even after a 2xx, and copies can arrive
concurrently. Each event carries a unique id (`X-Razorpay-Event-Id`).

Most of what an event does is already idempotent by itself: the order's state machine ignores a
second `Captured` (it is `PAID` already), and an entitlement grant is an upsert. But not all of
it:

- a **late copy of the original `payment.captured` arriving after a refund** would be ignored by
  the order (`REFUNDED` outranks `PAID`), but only because the order got there first. A future
  handler that doesn't go through the order (say, a coupon counter) would double-count;
- two copies processed at the same moment both read `PENDING` unless something serializes them;
- we want to **know** that an event was processed, for support ("did we get the webhook?").

## Decision

1. A table `payment_webhook_event(provider, event_id)` with a **primary key** on the pair.
2. The webhook handler, after verifying the signature over the raw body, runs **one transaction**:
   `INSERT … ON CONFLICT DO NOTHING` the event id, and only if the insert affected a row, apply
   the event to the order (which locks the order row, grants or revokes, and writes the outbox).
3. A copy that loses the insert answers **200** without doing anything. Concurrent copies block on
   the uncommitted key until the winner commits (then they see the row) or rolls back (then one of
   them claims it).
4. If processing throws, the claim **rolls back with it**, the endpoint answers 500, and the
   provider's redelivery claims it again. A claim exists only for an event whose effects
   committed.
5. Events we don't act on (unknown types, orders that aren't ours) are claimed too and answered
   200, so the provider stops retrying them.
6. A missing event id header falls back to the SHA-256 of the body: identical bodies are the same
   event.

## Alternatives rejected

| Option | Why not |
|---|---|
| **Process, then record the id** | A crash between the two re-processes the event on redelivery. Recording after means the dedupe is only as good as the effects' own idempotency. |
| **Claim in its own transaction, then process** | A claim committed before the processing failed marks an event done that never happened: the redelivery is dropped and the learner who paid has no course. |
| **Redis `SETNX` on the event id** | A second store with its own failure modes, outside the transaction: the same "claimed but not processed" gap, plus eviction. Postgres already orders the writes we care about. |
| **Rely on the effects being idempotent** | They are, today, through the order's state. But that depends on every future effect going through the order, and gives no audit trail. The claim is one insert. |
| **Dedupe on the payment id** | One payment produces several events (`payment.authorized`, `payment.captured`, `order.paid`, `refund.processed`), which are not duplicates of each other. |

## Consequences

- One extra insert per webhook; the table grows by one row per event (a retention job can delete
  rows older than the provider's retry window, 24 h, plus margin; not needed yet).
- 9.8's proof: 50 concurrent copies of one signed webhook → one claim, one `PAID` transition, one
  grant, one `OrderPaid` in the outbox.
