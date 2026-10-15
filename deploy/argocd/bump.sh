#!/bin/sh
# GitOps deploy: point the local cluster at TAG by committing it (`make k8s-bump TAG=sha-abc1234`).
#
# Refuses a tag that isn't published for ALL THREE images on GHCR: Argo CD would sync it and the
# pods would sit in ImagePullBackOff. (That's not hypothetical: CI once failed to publish two phases'
# images, devops/06 §5.6.) The commit is yours; push it and open a PR — the merge is the deploy.
set -eu
TAG="${1:?usage: bump.sh sha-<commit> | X.Y.Z}"
OWNER="${OWNER:-b-nimai}"
VALUES=deploy/helm/masternova/values-local.yaml

for app in api worker web; do
  repo="$OWNER/masternova-spring-$app"
  # an anonymous pull token: the packages are public, exactly what the cluster will use
  token=$(curl -sf "https://ghcr.io/token?scope=repository:$repo:pull" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" \
    -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json' \
    "https://ghcr.io/v2/$repo/manifests/$TAG")
  [ "$code" = 200 ] || { echo "ghcr.io/$repo:$TAG not found (HTTP $code): did CI publish this commit?" >&2; exit 1; }
  echo "ok  ghcr.io/$repo:$TAG"
done

current=$(sed -n 's/^  tag: \([^ ]*\).*/\1/p' "$VALUES")
[ "$current" != "$TAG" ] || { echo "already at $TAG"; exit 0; }
sed -i.bak "s/^  tag: $current\(.*\)/  tag: $TAG\1/" "$VALUES" && rm -f "$VALUES.bak"
git add "$VALUES"
git commit -q -m "deploy(local): $current → $TAG" -- "$VALUES"
echo "committed: $current → $TAG. Push + PR; Argo CD syncs main within a minute of the merge."
