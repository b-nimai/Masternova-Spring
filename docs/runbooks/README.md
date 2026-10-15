# Runbooks

**Last updated:** 2026-10-15

One runbook per alert, written in Phase D3 when the alert is created. Each one answers four
questions:

1. What does this alert mean for users?
2. How do I confirm it? (dashboard, query, `kubectl` command)
3. What do I do right now? (mitigate first)
4. How do I fix it for good?

| Alert | Severity | Runbook |
|---|---|---|
| `ApiAvailabilityFastBurn` / `…SlowBurn` | page / ticket | [api-availability.md](api-availability.md) |
| `ApiLatencyFastBurn` / `…SlowBurn` | page / ticket | [api-latency.md](api-latency.md) |
| `OutboxFreshnessFastBurn` / `…SlowBurn` | page / ticket | [outbox-freshness.md](outbox-freshness.md) |
| `ServiceDown` | page | [service-down.md](service-down.md) |
| `OutboxStalled` | page | [outbox-stalled.md](outbox-stalled.md) |
| `DeadLetters` | ticket | [dead-letters.md](dead-letters.md) |

Every alert's `runbook_url` annotation links here, so the notification email carries the link.
Locally, notifications land in Mailpit (<http://localhost:8026>) through Alertmanager
(<http://localhost:9094>).

## Operations

| Task | Runbook |
|---|---|
| Deploy (GitOps), roll back, failed migration, drain a node | [deploy-and-rollback.md](deploy-and-rollback.md) |
