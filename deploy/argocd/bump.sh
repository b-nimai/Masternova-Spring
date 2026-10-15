#!/bin/sh
# GitOps deploy: point the local cluster at TAG by committing it (`make k8s-bump TAG=sha-abc1234`).
#
# Refuses a tag that isn't published for ALL THREE images on GHCR, for BOTH platforms: Argo CD
# would sync it and the pods would sit in ImagePullBackOff. (Neither is hypothetical: CI once failed
# to publish two phases' images, devops/06 §5.6; and the first deploy from GHCR found amd64-only
# images on arm64 nodes, §7.) The commit is yours; push it and open a PR — the merge is the deploy.
set -eu
TAG="${1:?usage: bump.sh sha-<commit> | X.Y.Z}"
OWNER="${OWNER:-b-nimai}"
VALUES=deploy/helm/masternova/values-local.yaml

for app in api worker web; do
  repo="$OWNER/masternova-spring-$app"
  # an anonymous pull token: the packages are public, exactly what the cluster will use
  token=$(curl -sf "https://ghcr.io/token?scope=repository:$repo:pull" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  index=$(curl -sf -H "Authorization: Bearer $token" \
    -H 'Accept: application/vnd.oci.image.index.v1+json' \
    "https://ghcr.io/v2/$repo/manifests/$TAG") ||
    { echo "ghcr.io/$repo:$TAG not found: did CI publish this commit?" >&2; exit 1; }
  # the k3d nodes run the laptop's architecture (arm64 on Apple silicon), a real cluster amd64
  for arch in amd64 arm64; do
    printf '%s' "$index" | grep -Eq "\"architecture\": *\"$arch\"" ||
      { echo "ghcr.io/$repo:$TAG has no linux/$arch image (built before multi-arch CI?)" >&2; exit 1; }
  done
  echo "ok  ghcr.io/$repo:$TAG (amd64, arm64)"
done

current=$(sed -n 's/^  tag: \([^ ]*\).*/\1/p' "$VALUES")
[ "$current" != "$TAG" ] || { echo "already at $TAG"; exit 0; }
sed -i.bak "s/^  tag: $current\(.*\)/  tag: $TAG\1/" "$VALUES" && rm -f "$VALUES.bak"
git add "$VALUES"
git commit -q -m "deploy(local): $current → $TAG" -- "$VALUES"
echo "committed: $current → $TAG. Push + PR; Argo CD syncs main within a minute of the merge."
