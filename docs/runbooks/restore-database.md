# Runbook: restore the database to a point in time

**Last updated:** 2026-10-24 (D5.4) · **Applies to:** the CNPG cluster `masternova-db` with the
Barman Cloud plugin · **Drill:** [`load/chaos/pitr-drill.sh`](../../load/chaos/pitr-drill.sh),
results in [resilience drills §4](../hld/04-resilience-drills.md)

Not an alert runbook: for when **data is wrong**, not when Postgres is down. A crashed or lost
primary is handled by CNPG's failover (drills §3); a restore is for an `UPDATE` without a `WHERE`, a
bad migration's data change, or a deleted course that must come back.

## What exists

| Piece | Where | What it gives |
|---|---|---|
| WAL archive | every finished WAL segment → `s3://masternova-backups/masternova-db/wals/`, and `archive_timeout: 60s` closes a segment at least once a minute | **RPO ≤ 60 s** on a quiet database (seconds when busy) |
| Base backups | `ScheduledBackup masternova-db-daily`, 03:00 UTC; on demand with a `Backup` | the starting point a restore replays WAL on top of |
| Retention | `ObjectStore.retentionPolicy: 7d` | any second in the last 7 days |
| S3 keys | Secret `masternova-backup-s3` (`ACCESS_KEY_ID`, `ACCESS_SECRET_KEY`), from `make k8s-secrets`: never from git | the plugin's access to the bucket |

Check that it's working:

```bash
kubectl -n masternova get cluster masternova-db \
  -o jsonpath='{range .status.conditions[?(@.type=="ContinuousArchiving")]}{.status} {.message}{"\n"}{end}'
kubectl -n masternova get backups                       # phase: completed
kubectl -n masternova exec -it masternova-db-1 -c postgres -- \
  psql -c "select archived_count, failed_count, last_archived_time, last_failed_time from pg_stat_archiver"
```

A rising `failed_count` with an old `last_archived_time` means **WAL is piling up on the
primary's disk** (nothing is lost yet; it's retried). Fix the object store first.

## Restore

**Never restore over the live cluster.** CNPG restores into a **new** cluster: compare, then
switch the apps over (or copy the rows back).

1. **Pick the target time**, in UTC, just *before* the damage: from the api's logs, the audit
   columns (`updated_at`), or the moment someone reports it. A target after the damage restores
   the damage.
2. **Stop the damage spreading:** if a bug is still writing, scale the api down or turn the
   feature off first.
3. **Create the restore cluster:**

   ```yaml
   apiVersion: postgresql.cnpg.io/v1
   kind: Cluster
   metadata: { name: masternova-db-pitr, namespace: masternova }
   spec:
     instances: 1
     imageName: ghcr.io/cloudnative-pg/postgresql:17-standard-trixie # the same as masternova-db
     storage: { size: 2Gi }
     bootstrap:
       recovery:
         source: origin
         recoveryTarget: { targetTime: "2026-10-24T04:07:41Z" }
     externalClusters:
       - name: origin
         plugin:
           name: barman-cloud.cloudnative-pg.io
           parameters: { barmanObjectName: masternova-backups, serverName: masternova-db }
   ```

   `kubectl -n masternova wait cluster/masternova-db-pitr --for=condition=Ready --timeout=40m`

4. **Check it** (`kubectl -n masternova exec -it masternova-db-pitr-1 -c postgres -- psql masternova`):
   the damaged rows look right, and the newest row is from just before the target.
5. **Use it**, the smallest way that works:
   - **a few rows wrong:** `pg_dump --data-only -t <table> --where …` from the restore,
     reviewed, applied to the live cluster in one transaction;
   - **everything wrong:** point the apps at the restore (`DATABASE_URL` →
     `masternova-db-pitr-rw`), after a deliberate decision that the writes since the target are
     lost or will be replayed.
6. **Delete the restore cluster** when done: `kubectl -n masternova delete cluster masternova-db-pitr`.

## RTO: where the time goes

Measured by the drill (a 10,000-course database, 68 MB; base backup on demand: 11 s):

| Run | Create → Ready | Where the time went |
|---|---:|---|
| 1: the restore pod landed on a node that had never run Postgres | **73 min** | pulling the barman sidecar (7 min 23 s) and the Postgres image (failed pulls and backoff, then 4 min 32 s) on this network; the restore itself took < 1 min once the images arrived |
| 2: images already on every node | **57 s** | download the base backup, replay WAL to the target, promote |

**The restore is fast; getting the software onto the node isn't.** Before relying on a restore
for an RTO, make sure every node can start Postgres quickly: a pre-pull DaemonSet, an image
cache/mirror near the cluster, or a node pool where Postgres already runs. Add the decision time
(noticing, picking the target) on top: that's usually the largest part.

## Caveats

- **The backups live in the same SeaweedFS as the media, in the same cluster.** That's a drill
  setup. A real deployment writes them to another account or region (S3 with object lock), or a
  lost cluster takes its backups with it.
- Changing the backup configuration needs a Postgres restart; the chart sets
  `primaryUpdateMethod: switchover`, so the standby is promoted first (drills §5).
