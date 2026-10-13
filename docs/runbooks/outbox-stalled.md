# Runbook — `OutboxStalled`

**Last updated:** 2026-10-13 · **Severity:** page · **Rule:** [`symptoms.rules.yml`](../../deploy/observability/prometheus/rules/symptoms.rules.yml)
**Fires when:** the most overdue *due* outbox event has waited more than 5 minutes past its due time, for 5 minutes (`max(outbox_overdue_seconds) > 300`; every app reports the gauge). A message waiting out its retry backoff is not due, so one failing message doesn't fire this; see [dead-letters](dead-letters.md).

## 1. What it means for users

**No** event consequences are happening: no emails, no video processing. This alert exists
because the freshness SLO can't see it: with zero deliveries there are no lag samples, and the
SLO's ratio becomes NaN (silent), not red.

## 2. Confirm

- **Pipeline & outbox** → *Outbox overdue* is climbing and *Delivery outcomes / s* is flat at
  zero.
- Is the worker up? `docker compose ps worker` / `curl localhost:8090/actuator/health`.
  `ServiceDown{job="worker"}` would fire too, and Alertmanager inhibits this alert when it does.
- Worker up but not relaying: is `masternova.outbox.relay-enabled` true in its config? Are rows
  stuck `PROCESSING` with a future `next_attempt_at` (a handler blocked past its lease)?
  `SELECT status, count(*), min(next_attempt_at) FROM outbox_message WHERE status IN
  ('PENDING','PROCESSING') GROUP BY status;`

## 3. Mitigate now

1. Worker down → start it (`docker compose up -d worker`); the backlog drains on its own.
2. Worker up, relay stuck → take a thread dump (`jcmd 1 Thread.print` in the container) to find
   the blocked handler, then restart the worker. Leases expire and the rows are reclaimed;
   handlers are idempotent, so a redelivery is safe.

## 4. Fix for good

A handler that can block needs a timeout. Add the blocking case to its tests.
