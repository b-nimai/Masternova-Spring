# Runbook — `ApiAvailabilityFastBurn` / `ApiAvailabilitySlowBurn`

**Last updated:** 2026-10-13 · **Severity:** fast burn = page, slow burn = ticket · **Rule:** [`slo.rules.yml`](../../deploy/observability/prometheus/rules/slo.rules.yml)
**SLO:** 99.5 % of api requests (excluding `/actuator` and SSE streams) succeed on the server (no 5xx), over 30 days.

## 1. What it means for users

Requests are failing with 5xx: pages show errors, saves don't stick, checkouts (Phase 9) fail.
**Fast burn** (≥ 14.4× over 1 h and 5 m): about 2 % of the month's error budget burned in an
hour, so the whole budget would last about two days. **Slow burn** (≥ 6× over 6 h and 30 m): a
smaller leak that adds up.

## 2. Confirm

- Grafana → **HTTP (RED)**: the *5xx ratio* stat and *5xx / s by route* (the route and exception
  tags say where).
- Grafana → **SLOs & error budgets**: the burn rate per window, and how much budget is left.
- Explore → Loki: `{service="api", level="ERROR"}`. Every line has a `traceId`; **Open trace**
  shows the failing request in Tempo.
- Is it one route or all? All of them points at a dependency: check the **Hikari** dashboard
  (*threads waiting*, *timeouts*) and `docker compose ps` (postgres, redis).

## 3. Mitigate now

1. **A deploy in the last hour?** Roll back to the previous image tag first and investigate after.
2. **Database:** pool saturated (`hikaricp_connections_pending > 0`) → find the long holder
   (*usage time* panel; `SELECT … FROM pg_stat_activity WHERE state <> 'idle'`).
3. **One route:** if it can be switched off or degraded (search, recommendations), do that.
4. **Storage down** gives 503 + `Retry-After` on media routes (`StorageExceptionHandler`), which
   is designed behaviour, but it still burns the budget: check SeaweedFS / S3.

## 4. Fix for good

Write a regression test reproducing the trace's failure. If the error was a dependency's,
decide whether it should become a 503 with `Retry-After` (designed, documented) or be retried.
