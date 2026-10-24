#!/usr/bin/env bash
# Chaos drill (D5.3): kill the worker in the middle of a transcode and prove the job still finishes,
# once (docs/hld/04-resilience-drills.md §1).
#
#   load/chaos/kill-worker-mid-transcode.sh            # kill -9 the JVM (a crash): the lease must lapse
#   GRACEFUL=1 load/chaos/kill-worker-mid-transcode.sh  # a normal delete: the job is released at once
#
# Uploads a generated 90 s 1080p video as the seeded instructor (straight to storage, like the
# browser), waits until the pipeline job is RUNNING with some progress, kills that worker pod, then
# times how long until the asset is READY and checks the job ran to DONE exactly once.
set -euo pipefail
BASE=${BASE_URL:-https://masternova.localhost:8443}
NS=masternova
CURL=(curl -sk --fail-with-body)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

psql() {
  kubectl -n $NS exec -i "$(kubectl -n $NS get pod -l cnpg.io/cluster=masternova-db,cnpg.io/instanceRole=primary -o name)" \
    -c postgres -- psql -qtA -d masternova -c "$1"
}
now() { date +%s; }

echo "→ a 90 s 1080p test video (ffmpeg testsrc2 + a tone)"
ffmpeg -loglevel error -f lavfi -i testsrc2=size=1920x1080:rate=30 -f lavfi -i sine=frequency=440 \
  -t 90 -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -shortest "$tmp/drill.mp4"
size=$(stat -f%z "$tmp/drill.mp4" 2>/dev/null || stat -c%s "$tmp/drill.mp4")

token=$("${CURL[@]}" "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"instructor@seed.test","password":"seed-password"}' | jq -r .accessToken)
auth=(-H "Authorization: Bearer $token")

echo "→ upload $((size / 1024 / 1024)) MiB"
"${CURL[@]}" "$BASE/api/v1/media/uploads" "${auth[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: drill-$(uuidgen)" \
  -d "{\"filename\":\"drill.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":$size}" >"$tmp/upload.json"
upload=$(jq -r .uploadId "$tmp/upload.json")
asset=$(jq -r .assetId "$tmp/upload.json")
part=$(jq -r .partSizeBytes "$tmp/upload.json")
jq -r '.parts[] | "\(.number) \(.url)"' "$tmp/upload.json" | while read -r n url; do
  dd if="$tmp/drill.mp4" of="$tmp/part" bs="$part" skip=$((n - 1)) count=1 2>/dev/null
  curl -sk --fail -X PUT --data-binary @"$tmp/part" "$url" -o /dev/null
done
"${CURL[@]}" -X POST "$BASE/api/v1/media/uploads/$upload/complete" "${auth[@]}" >/dev/null
echo "  asset $asset"

echo "→ wait for the transcode to be RUNNING with progress"
job=""
for _ in $(seq 1 120); do
  row=$(psql "SELECT id, worker_id, coalesce(progress,0) FROM pipeline_job
              WHERE asset_id = '$asset' AND type = 'TRANSCODE' AND status = 'RUNNING'
              ORDER BY progress DESC NULLS LAST LIMIT 1")
  if [ -n "$row" ] && [ "$(cut -d'|' -f3 <<<"$row")" -ge 10 ]; then job=$row; break; fi
  sleep 1
done
[ -n "$job" ] || { echo "the transcode never reached RUNNING ≥ 10 %"; exit 1; }
IFS='|' read -r job_id worker_id progress <<<"$job"
pod=$(kubectl -n $NS get pods -l app.kubernetes.io/name=worker -o name | grep -F "${worker_id%%:*}" || true)
pod=${pod:-$(kubectl -n $NS get pods -l app.kubernetes.io/name=worker -o name | head -1)}
echo "  job $job_id at $progress % on $worker_id ($pod)"

killed=$(now)
if [ "${GRACEFUL:-0}" = 1 ]; then
  echo "→ delete $pod (graceful: SIGTERM, the runner releases its job)"
  kubectl -n $NS delete "$pod" --wait=false
else
  # ⭐ NOT `kubectl delete --force --grace-period=0`: that removes the API object at once, but the
  #    kubelet still sends SIGTERM with a ~1-2 s grace, so the worker releases its jobs (the graceful
  #    path). A crash (OOM kill, node loss) gets no signal at all: kill -9 the JVM from its node.
  node=$(kubectl -n $NS get "$pod" -o jsonpath='{.spec.nodeName}')
  cid=$(kubectl -n $NS get "$pod" -o jsonpath='{.status.containerStatuses[0].containerID}' | sed 's#.*://##')
  pid=$(docker exec "$node" crictl inspect --output go-template --template '{{.info.pid}}' "$cid")
  echo "→ kill -9 the worker JVM (pid $pid on $node): no release, the 2 min lease has to lapse"
  docker exec "$node" kill -9 "$pid"
fi

echo "→ wait for READY"
for _ in $(seq 1 600); do
  status=$(psql "SELECT status FROM media_asset WHERE id = '$asset'")
  [ "$status" = READY ] && break
  sleep 2
done
ready=$(now)
echo
echo "asset: $status after $((ready - killed)) s from the kill"
psql "SELECT type, rung, status, attempts, worker_id FROM pipeline_job WHERE asset_id = '$asset' ORDER BY created_at, rung" |
  sed 's/^/  job: /'
[ "$status" = READY ]
