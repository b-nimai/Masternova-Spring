# Demo: 3 minutes

**Last updated:** 2026-10-24 (D5.6) · **Status:** script ready, video not recorded yet

A screen recording for a portfolio or an interview. One idea per beat: *it works, it's designed,
it survives.* Record against the k3d cluster (`https://masternova.localhost:8443`) at 1440 × 900,
with the terminal on the right third of the screen.

**Before recording**

```bash
make k8s-seed COURSES=200                     # a catalog that looks real, the seeded accounts
# a second browser profile logged in as instructor@seed.test / seed-password
# terminal: `kubectl -n masternova get pods -w` in one pane, k6 ready in another
```

| Time | On screen | Say (roughly) |
|---|---|---|
| **0:00–0:15** | Home page, scroll once | "Masternova: sell access to a video, and make sure only people who paid can watch it. The interesting part is the backend under it." |
| **0:15–0:45** | Instructor: new course → curriculum → upload a video; the progress bar; switch to `kubectl … pods -w` while it transcodes | "The browser uploads straight to S3 with presigned parts. The api only plans and completes. A worker claims the transcode with a lease and heartbeats; ffmpeg builds the HLS ladder." |
| **0:45–1:15** | Learner (incognito): course page → buy → Razorpay test checkout → the lecture plays | "Checkout is an idempotency key plus a transactional outbox: the order and its event commit together, so a retry never charges twice. The player gets a short-lived ticket; an entitlement policy chain decides, cached in Redis." |
| **1:15–1:30** | Instructor: undo / redo in the curriculum | "Curriculum edits are Command objects that return their own inverse. That's undo, with a Memento for deletes." |
| **1:30–2:15** | Terminal: `RATE=300 k6 run load/slo-mix.js`; then `load/chaos/kill-under-load.sh api` and the result line | "At 300 requests a second, twice what this laptop can serve, it degrades instead of collapsing: a bulkhead answers 503 at once, where it used to queue 1,500 requests per pod and restart. Kill an api pod under load: 42 requests out of 18,000." |
| **2:15–2:40** | `docs/hld/04-resilience-drills.md` §4 on screen, then `kubectl get backups` | "Postgres archives every WAL segment to S3. The drill deletes a row and restores a new cluster to the second before: back in 57 seconds." |
| **2:40–3:00** | GitHub: a green CI run (images, Trivy, ZAP, gitleaks, cosign); Argo CD synced | "Every merge is built, scanned and signed. Deploying is a commit: Argo CD syncs it. Every number here is measured, and the docs say how." |

**Cut first if it runs long:** the undo/redo beat (1:15–1:30).
