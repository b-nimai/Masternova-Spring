#!/usr/bin/env bash
# Backup + point-in-time restore drill (D5.4, docs/runbooks/restore-database.md).
#
#   load/chaos/pitr-drill.sh
#
# 1. a fresh base backup (on demand, like the daily ScheduledBackup);
# 2. a marker row, then T (the target), then an "accident": the marker deleted after T;
# 3. the WAL holding the accident is archived;
# 4. a NEW cluster, masternova-db-pitr, restores from the bucket to T;
# 5. the marker must be there (the accident undone), and the restore is timed (RTO).
# The live database is never touched except for the drill schema, dropped at the end.
set -euo pipefail
NS=masternova
SRC=masternova-db
DST=masternova-db-pitr

primary() { kubectl -n $NS get pod -l cnpg.io/cluster="$1",cnpg.io/instanceRole=primary -o name; }
sql() { kubectl -n $NS exec -i "$(primary "$1")" -c postgres -- psql -qtA -d masternova -v ON_ERROR_STOP=1 -c "$2"; }
now() { date +%s; }

# the drill schema goes even when a step fails (a restore that times out, say)
cleanup() { sql $SRC "DROP SCHEMA IF EXISTS drill CASCADE" >/dev/null || true; }
trap cleanup EXIT

echo "→ base backup (on demand)"
backup=drill-$(date +%Y%m%d%H%M%S)
kubectl -n $NS apply -f - <<EOF
apiVersion: postgresql.cnpg.io/v1
kind: Backup
metadata: { name: $backup }
spec:
  cluster: { name: $SRC }
  method: plugin
  pluginConfiguration: { name: barman-cloud.cloudnative-pg.io }
EOF
t0=$(now)
kubectl -n $NS wait backup/$backup --for=jsonpath='{.status.phase}'=completed --timeout=15m
echo "  completed in $(($(now) - t0)) s"

echo "→ marker, target time, accident"
sql $SRC "CREATE SCHEMA IF NOT EXISTS drill; CREATE TABLE IF NOT EXISTS drill.marker (note text, at timestamptz DEFAULT now());
          INSERT INTO drill.marker (note) VALUES ('$backup');"
sleep 2
target=$(sql $SRC "SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')")
sleep 2
sql $SRC "DELETE FROM drill.marker WHERE note = '$backup'" # the accident, after the target
echo "  target $target"

echo "→ archive the WAL that holds the accident"
wal=$(sql $SRC "SELECT pg_walfile_name(pg_switch_wal())")
for _ in $(seq 1 60); do
  done_wal=$(sql $SRC "SELECT coalesce(last_archived_wal, '') FROM pg_stat_archiver")
  [[ "$done_wal" > "$wal" || "$done_wal" == "$wal" ]] && break
  sleep 2
done
echo "  archived up to $done_wal (needed $wal)"
if [[ "$done_wal" < "$wal" ]]; then
  echo "the WAL holding the target was never archived: check ContinuousArchiving" >&2
  exit 1
fi

echo "→ restore into a new cluster, $DST, at the target"
kubectl -n $NS delete cluster $DST --ignore-not-found --wait
t1=$(now)
kubectl -n $NS apply -f - <<EOF
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata: { name: $DST }
spec:
  instances: 1
  imageName: $(kubectl -n $NS get cluster $SRC -o jsonpath='{.spec.imageName}')
  storage: { size: $(kubectl -n $NS get cluster $SRC -o jsonpath='{.spec.storage.size}') }
  bootstrap:
    recovery:
      source: origin
      recoveryTarget: { targetTime: "$target" }
  externalClusters:
    - name: origin
      plugin:
        name: barman-cloud.cloudnative-pg.io
        parameters: { barmanObjectName: masternova-backups, serverName: $SRC }
EOF
# 40 min: a restore lands on whichever node the scheduler picks, and a node that never ran Postgres
# pulls the image (and the sidecar's) first. On this network that alone was 20 min (D5.4).
kubectl -n $NS wait cluster/$DST --for=condition=Ready --timeout=40m
rto=$(($(now) - t1))

echo "→ verify"
found=$(sql $DST "SELECT count(*) FROM drill.marker WHERE note = '$backup'")
courses=$(sql $DST "SELECT count(*) FROM course")
live=$(sql $SRC "SELECT count(*) FROM course")
echo
echo "marker in the restore: $found (want 1) · courses: $courses restored, $live live"
echo "restore (RTO, excluding the decision to restore): ${rto} s"
sql $SRC "SELECT 'archiver: ' || archived_count || ' archived, ' || failed_count || ' failed, last ' || coalesce(last_archived_time::text, '-') FROM pg_stat_archiver"

echo "→ clean up"
[ "${KEEP:-0}" = 1 ] || kubectl -n $NS delete cluster $DST --wait
[ "$found" = 1 ]
