#!/usr/bin/env bash
# Chaos drills under load (D5.3, docs/hld/04-resilience-drills.md):
#
#   load/chaos/kill-under-load.sh api       # kill -9 one api JVM at t=60 s of a 3 min, 100 rps run
#   load/chaos/kill-under-load.sh primary   # kill -9 the primary's container (instance manager + postmaster)
#   GRACEFUL=1 load/chaos/kill-under-load.sh …  # a pod delete instead (SIGTERM, the normal path)
#
# Prints what clients saw (k6: failed requests, p95, max) and, for the database, how long until a
# new primary was serving.
set -euo pipefail
target=${1:?api | primary}
NS=masternova
RATE=${RATE:-100}
out=$(cd "$(dirname "$0")/.." && pwd)/results/chaos-$target-$(date +%Y%m%d-%H%M%S)
mkdir -p "$out"

(cd "$(dirname "$0")/.." && RATE=$RATE DURATION=3m k6 run --quiet --summary-export "$out/k6.json" slo-mix.js \
  >"$out/k6.log" 2>&1) &
k6pid=$!
sleep 60

# ⭐ a crash gets no signal: kill -9 the container's main process from its node. (`kubectl delete
#    --force --grace-period=0` still sends SIGTERM with a ~1-2 s grace: a fast graceful stop.)
kill9() {
  local pod=$1 container=$2 node cid pid
  node=$(kubectl -n $NS get "$pod" -o jsonpath='{.spec.nodeName}')
  cid=$(kubectl -n $NS get "$pod" -o jsonpath="{.status.containerStatuses[?(@.name=='$container')].containerID}" | sed 's#.*://##')
  pid=$(docker exec "$node" crictl inspect --output go-template --template '{{.info.pid}}' "$cid")
  docker exec "$node" kill -9 "$pid"
}
stop() { # pod container
  if [ "${GRACEFUL:-0}" = 1 ]; then kubectl -n $NS delete "$1" --wait=false; else kill9 "$1" "$2"; fi
}

case $target in
  api)
    victim=$(kubectl -n $NS get pods -l app.kubernetes.io/name=api -o name | head -1)
    echo "→ $([ "${GRACEFUL:-0}" = 1 ] && echo delete || echo kill -9) $victim at $(date +%T)"
    stop "$victim" api
    ;;
  primary)
    victim=$(kubectl -n $NS get pod -l cnpg.io/cluster=masternova-db,cnpg.io/instanceRole=primary -o name)
    echo "→ $([ "${GRACEFUL:-0}" = 1 ] && echo delete || echo kill -9) the primary $victim at $(date +%T)"
    killed=$(date +%s)
    stop "$victim" postgres
    seen_down=0
    for _ in $(seq 1 180); do
      now_primary=$(kubectl -n $NS get cluster masternova-db -o jsonpath='{.status.currentPrimary}')
      ready=$(kubectl -n $NS get pod "$now_primary" -o jsonpath='{.status.containerStatuses[0].ready}' 2>/dev/null || true)
      [ "pod/$now_primary" = "$victim" ] && [ "$ready" != true ] && seen_down=1
      [ "pod/$now_primary" != "$victim" ] && [ "$ready" = true ] && break
      # a crashed container is restarted in place by the kubelet; CNPG may keep it as the primary
      [ "pod/$now_primary" = "$victim" ] && [ "$seen_down" = 1 ] && [ "$ready" = true ] && break
      sleep 1
    done
    echo "  primary $now_primary ready after $(($(date +%s) - killed)) s"
    ;;
  *) echo "api | primary"; exit 2 ;;
esac

wait $k6pid || true # thresholds may fail: that's the measurement, not an error
jq -r '.metrics as $m
  | "requests \($m["http_reqs{scenario:mix}"].count) · failed \(($m["http_req_failed{scenario:mix}"].value // 0) * 100 | . * 100 | round / 100) % (\($m["http_req_failed{scenario:mix}"].passes // 0) requests)"
  + " · p95 \($m["http_req_duration{scenario:mix}"]["p(95)"] | round) ms · max \($m["http_req_duration{scenario:mix}"].max | round) ms"
  + " · dropped \($m.dropped_iterations.count // 0)"' "$out/k6.json"
grep -oE 'status=[0-9]+' "$out/k6.log" | sort | uniq -c || true
echo "raw: $out"
