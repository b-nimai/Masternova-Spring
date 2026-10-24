# Shared by the chaos drills (D5.3, D5.4): `source "$(dirname "$0")/lib.sh"`.
NS=${NS:-masternova}

# The CNPG cluster's CURRENT primary pod (after a failover it's no longer instance 1).
primary() { kubectl -n "$NS" get pod -l cnpg.io/cluster="${1:-masternova-db}",cnpg.io/instanceRole=primary -o name; }

# sql <cluster> <statement>: psql as the superuser over the primary's local socket.
sql() { kubectl -n "$NS" exec -i "$(primary "$1")" -c postgres -- psql -qtA -d masternova -v ON_ERROR_STOP=1 -c "$2"; }

# kill9 <pod> <container>: a crash. No signal reaches the process, unlike `kubectl delete --force
# --grace-period=0`, which still sends SIGTERM with a 1-2 s grace (a fast graceful stop).
kill9() {
  local pod=$1 container=$2 node cid pid
  node=$(kubectl -n "$NS" get "$pod" -o jsonpath='{.spec.nodeName}')
  cid=$(kubectl -n "$NS" get "$pod" -o jsonpath="{.status.containerStatuses[?(@.name=='$container')].containerID}" | sed 's#.*://##')
  pid=$(docker exec "$node" crictl inspect --output go-template --template '{{.info.pid}}' "$cid")
  docker exec "$node" kill -9 "$pid"
}
