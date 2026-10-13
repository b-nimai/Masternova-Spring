# Runbook — `ServiceDown`

**Last updated:** 2026-10-13 · **Severity:** page · **Rule:** [`symptoms.rules.yml`](../../deploy/observability/prometheus/rules/symptoms.rules.yml)
**Fires when:** no instance of `api` or `worker` answers Prometheus' scrape for 2 minutes (`max by (job)(up) == 0`).

## 1. What it means for users

- **api**: the whole product is down for every user.
- **worker**: the site works, but nothing happens afterwards (emails, video processing).

Alertmanager inhibits the SLO alerts this outage causes, so you get the cause, not every echo.

## 2. Confirm

- `docker compose ps` (later `kubectl get pods`): exited? restarting? unhealthy?
- `docker compose logs --tail 100 <service>`, or Loki `{service="<service>"}`. Look for
  `OutOfMemoryError` (the JVM exits on it: `ExitOnOutOfMemoryError`), Flyway failures (api), or a
  failed connection to postgres/redis.
- `docker inspect <container> --format '{{.State.OOMKilled}}'`: `true` means the kernel killed it
  at the memory limit (devops note 02: heap + non-heap > limit).
- Up, but Prometheus can't reach it? Check Prometheus → *Status → Targets* for the scrape error.

## 3. Mitigate now

1. Crash loop after a deploy → roll back the image tag.
2. OOM → raise the container's memory limit (and keep `MaxRAMPercentage`), restart.
3. A dependency is down (postgres) → fix that first; the app's readiness probe recovers by
   itself.

## 4. Fix for good

Find the trigger in the logs before the crash; reproduce the OOM with a heap dump
(`-XX:+HeapDumpOnOutOfMemoryError` on a test run).
