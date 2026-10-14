# Runbook — `OutboxFreshnessFastBurn` / `OutboxFreshnessSlowBurn`

**Last updated:** 2026-10-13 · **Severity:** fast burn = page, slow burn = ticket · **Rule:** [`slo.rules.yml`](../../deploy/observability/prometheus/rules/slo.rules.yml)
**SLO:** 99 % of outbox events are handled within 60 s of the commit that wrote them, over 30 days. Events given up on (DEAD) count as bad.

## 1. What it means for users

Things that happen *after* an action are late: the verification email, the welcome email, the
start of video processing after an upload. The action itself succeeded (the api is fine); its
consequences are queued.

## 2. Confirm

- **Pipeline & outbox** → *Commit → delivered* quantiles, *Delivery outcomes / s* (lots of
  `retried` = a handler's dependency is failing), *Backlog and overdue*, *Dead letters / h* (DEAD events burn this budget too).
- Retries: `SELECT event_type, attempts, last_error FROM outbox_message WHERE status = 'PENDING'
  AND attempts > 1 ORDER BY occurred_at LIMIT 20;`
- One slow delivery: Loki `{service="worker"} |= "failed attempt"` → **Open trace**. The
  trace starts at the api request that wrote the event.

## 3. Mitigate now

1. A handler's dependency is down (SMTP / Mailpit, Resend, storage): fix or fail over to it.
   Messages retry with backoff and are delivered once it is back; nothing is lost.
2. Throughput: the backlog is growing with no errors → raise `masternova.outbox.batch-size` or
   run a second worker replica. Claims use `FOR UPDATE SKIP LOCKED`, so replicas never collide.
3. If no deliveries happen at all, see [outbox-stalled](outbox-stalled.md).

## 4. Fix for good

Make the slow handler fast or asynchronous itself, give its dependency a timeout shorter than the
lease (60 s), and add the case to the handler's tests.
