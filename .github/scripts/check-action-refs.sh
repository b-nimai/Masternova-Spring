#!/usr/bin/env bash
# Every `uses: owner/repo@ref` in the workflows must resolve to a real tag or branch.
# actionlint validates syntax and inputs but not whether a version exists: a missing tag
# (e.g. a major tag the action never publishes) only fails when that job starts.
set -euo pipefail
status=0
while read -r use; do
  repo=$(cut -d@ -f1 <<< "$use" | cut -d/ -f1-2)
  ref=$(cut -d@ -f2 <<< "$use")
  if gh api "repos/$repo/git/ref/tags/$ref" > /dev/null 2>&1 \
    || gh api "repos/$repo/git/ref/heads/$ref" > /dev/null 2>&1 \
    || gh api "repos/$repo/commits/$ref" > /dev/null 2>&1; then
    echo "ok       $use"
  else
    echo "::error::$use does not resolve to a tag, branch or commit of $repo"
    status=1
  fi
done < <(grep -hoE 'uses: *[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[^ #]+' .github/workflows/*.yml | sed -E 's/uses: *//' | sort -u)
exit $status
