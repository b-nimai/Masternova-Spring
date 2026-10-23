#!/usr/bin/env bash
# The capacity sweep (D5.1): load/slo-mix.js at each arrival rate in turn, one markdown row per step,
# so the knee (the last rate that still meets the SLOs) can be read off one table.
#
#   load/capacity-sweep.sh 50 100 200 300 400          # rates in requests/s
#   DURATION=2m PAUSE=60 load/capacity-sweep.sh 100 200
#
# PAUSE (s) between steps lets the HPA's scale-down window and the JIT settle less than it lets the
# previous step's backlog drain; the HPA's state is printed per step because it changes the result.
set -euo pipefail
cd "$(dirname "$0")"
out="results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"

echo "| rate/s | achieved/s | p50 ms | p95 ms | p99 ms | max ms | failed | dropped | api pods | SLOs |"
echo "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"
for rate in "$@"; do
  ready() { kubectl -n masternova get deploy api -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "?"; }
  pods=$(ready)
  if RATE=$rate k6 run --quiet --summary-export "$out/$rate.json" slo-mix.js >"$out/$rate.log" 2>&1; then
    verdict="✅"
  else
    verdict="❌"
  fi
  pods="${pods}→$(ready)" # the HPA may scale during a step: start → end
  jq -r --arg rate "$rate" --arg pods "$pods" --arg v "$verdict" '
    .metrics as $m
    | $m["http_req_duration{scenario:mix}"] as $d
    | [$rate,
       ($m["http_reqs{scenario:mix}"].rate | floor),
       ($d.med | . * 10 | round / 10),
       ($d["p(95)"] | . * 10 | round / 10),
       ($d["p(99)"] | . * 10 | round / 10),
       ($d.max | round),
       (($m["http_req_failed{scenario:mix}"].value // 0) * 100 | . * 100 | round / 100 | tostring + " %"),
       ($m.dropped_iterations.count // 0),
       $pods, $v]
    | "| " + (map(tostring) | join(" | ")) + " |"' "$out/$rate.json"
  sleep "${PAUSE:-30}"
done
echo
echo "raw summaries and logs: load/$out/"
