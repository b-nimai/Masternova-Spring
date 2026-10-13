# Runbook — `DeadLetters`

**Last updated:** 2026-10-13 · **Severity:** ticket · **Rule:** [`symptoms.rules.yml`](../../deploy/observability/prometheus/rules/symptoms.rules.yml)
**Fires when:** an outbox message was parked as `DEAD`, or a pipeline job was buried, in the last hour (label `kind` = `outbox` | `pipeline`).

## 1. What it means for users

One specific user is missing something for good: an email (`outbox`) or a playable video
(`pipeline`: the asset is `FAILED`, the instructor sees "processing failed"). Retries already
ran out, so nothing will fix this by itself.

## 2. Confirm

- **outbox:** `SELECT id, event_type, attempts, last_error FROM outbox_message WHERE status =
  'DEAD' ORDER BY processed_at DESC LIMIT 20;`
- **pipeline:** `GET /api/v1/admin/media/jobs/dead` (ADMIN) lists buried jobs with their last
  error. `MEDIA_UNREADABLE` = the file itself is bad (permanent); `TRANSCODE_FAILED` = the
  attempts ran out.
- Loki: `{service="worker", level="ERROR"} |= "DEAD"`. The line carries the `traceId` of the
  delivery or job.

## 3. Mitigate now

- **pipeline, transient cause (storage was down):** `POST /api/v1/admin/media/jobs/{id}/replay`
  re-runs the job with a fresh attempt budget (Phase 7.6).
- **pipeline, bad media:** nothing to replay. The instructor has to upload a working file.
- **outbox:** fix the handler's dependency, then reset the row:
  `UPDATE outbox_message SET status = 'PENDING', attempts = 0, next_attempt_at = now() WHERE id = '…';`.
  Handlers are idempotent (the per-email claim), so a resend can't duplicate.

## 4. Fix for good

If the same `last_error` keeps appearing, the handler needs to classify it: permanent (stop
early, tell the user) or temporary (longer backoff).
