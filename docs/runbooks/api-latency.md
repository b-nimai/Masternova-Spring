# Runbook — `ApiLatencyFastBurn` / `ApiLatencySlowBurn`

**Last updated:** 2026-10-13 · **Severity:** fast burn = page, slow burn = ticket · **Rule:** [`slo.rules.yml`](../../deploy/observability/prometheus/rules/slo.rules.yml)
**SLO:** 95 % of api requests answer within 300 ms, over 30 days (counted at the exact `le="0.3"` bucket edge).

## 1. What it means for users

Pages feel slow and spinners hang. A fast burn means more than 72 % of requests are over 300 ms
(14.4 × the 5 % budget): the app is close to unusable.

## 2. Confirm

- **HTTP (RED)** → *Latency quantiles* and the *Slowest routes* table: is it one route or all?
- **Hikari** → *threads waiting* and *acquire time*. With virtual threads, requests rarely run
  out of threads; they queue for one of the 10 connections instead.
- **JVM** → *GC pause time per second* (over ~5 % = heap pressure) and *CPU*.
- Tempo: search for traces of the slow route with `duration > 300ms`; the span waterfall shows
  where the time goes.

## 3. Mitigate now

1. A recent deploy → roll back.
2. Pool saturated by one slow query → find it in `pg_stat_activity`; cancel it with
   `SELECT pg_cancel_backend(pid)` if it is a runaway report.
3. GC-bound → more memory for the container (heap = 75 % of the limit, devops note 02), then
   look for the allocation source.
4. A traffic spike → scale out api replicas (D4: HPA).

## 4. Fix for good

An `EXPLAIN (ANALYZE, BUFFERS)` of the slow query plus an index or keyset pagination
(ADR-0009); or move the slow part out of the request into the outbox and the worker.
