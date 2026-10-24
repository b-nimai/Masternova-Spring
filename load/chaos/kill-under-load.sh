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
source "$(dirname "$0")/lib.sh"
RATE=${RATE:-100}
out=$(cd "$(dirname "$0")/.." && pwd)/results/chaos-$target-$(date +%Y%m%d-%H%M%S)
mkdir -p "$out"

(cd "$(dirname "$0")/.." && RATE=$RATE DURATION=3m k6 run --quiet --summary-export "$out/k6.json" slo-mix.js \
  >"$out/k6.log" 2>&1) &
k6pid=$!
sleep 60

verb=$([ "${GRACEFUL:-0}" = 1 ] && echo delete || echo "kill -9")
stop() { # pod container
  if [ "${GRACEFUL:-0}" = 1 ]; then kubectl -n $NS delete "$1" --wait=false; else kill9 "$1" "$2"; fi
}

case $target in
  api)
    victim=$(kubectl -n $NS get pods -l app.kubernetes.io/name=api -o name | head -1)
    echo "→ $verb $victim at $(date +%T)"
    stop "$victim" api
    ;;
  primary)
    victim=$(primary)
    echo "→ $verb the primary $victim at $(date +%T)"
    killed=$(date +%s)
    stop "$victim" postgres
    seen_down=0 recovered=0
    for _ in $(seq 1 180); do
      now_primary=$(kubectl -n $NS get cluster masternova-db -o jsonpath='{.status.currentPrimary}')
      ready=$(kubectl -n $NS get pod "$now_primary" -o jsonpath='{.status.containerStatuses[0].ready}' 2>/dev/null || true)
      [ "pod/$now_primary" = "$victim" ] && [ "$ready" != true ] && seen_down=1
      # a new primary, or (a crash) the same one back after it was seen down: restarted in place
      if [ "$ready" = true ] && { [ "pod/$now_primary" != "$victim" ] || [ "$seen_down" = 1 ]; }; then
        recovered=1
        break
      fi
      sleep 1
    done
    if [ "$recovered" = 1 ]; then
      echo "  primary $now_primary ready after $(($(date +%s) - killed)) s"
    else
      echo "  NO primary ready after $(($(date +%s) - killed)) s (gave up waiting)" >&2
    fi
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
