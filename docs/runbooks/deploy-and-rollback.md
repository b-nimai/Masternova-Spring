# Runbook: deploy, roll back, drain a node (Kubernetes, GitOps)

**Last updated:** 2026-10-15 · **Applies to:** the k3d cluster managed by Argo CD (Phase D4) ·
**Background:** [DevOps note 06](../../patterns/devops/06-kubernetes.md) §7–§8

Not an alert runbook: the routine operations, written so they're boring.

## Deploy a new version

1. CI built, scanned, signed and pushed the commit's images (`main` only). Check that the run
   on the merge commit is green, including the `images` job.
2. `make k8s-bump TAG=sha-<7 chars>`. It refuses a tag that isn't on GHCR for **all three**
   images, then commits the change to `deploy/helm/masternova/values-local.yaml` as you.
3. Push + PR + merge. Argo CD polls `main` every 60 s: the `db-migrate` PreSync Job runs first,
   then the Deployments roll.
4. Watch it:

   ```bash
   kubectl -n argocd get app masternova            # Synced / Healthy, and the revision
   kubectl -n masternova logs job/db-migrate       # "Successfully applied N migrations" / "up to date"
   kubectl -n masternova rollout status deploy/api
   ```

**Before merging a migration:** `MigrationSafetyTest` already refused destructive DDL. If the PR
has a `-- contract: V<n>` file, confirm that **every running version** stopped reading what it
drops (the switch release is fully rolled out).

## Roll back

- **The image only (no migration in between):** `git revert` the bump commit, PR, merge. Argo
  syncs the previous tag.
- ⭐ **Past a migration: don't revert the image blindly.** The schema stays where the migration
  left it, and the old image may not run against it (measured in D4.8: the old code failed 70 %
  of requests against a renamed column). Roll **forward** with a fix, or, if the migration was
  an *expand* (additive), the previous image still works: revert the bump.
- Never "fix" a failed sync with `kubectl edit`: `selfHeal` reverts it within seconds. Change
  git. For an emergency hand-edit, pause automation **on the root first**: it manages the
  `masternova` Application and would revert a patch to it just as fast.

  ```bash
  off='{"spec":{"syncPolicy":{"automated":null}}}'
  kubectl -n argocd patch app masternova-root --type merge -p "$off"
  kubectl -n argocd patch app masternova      --type merge -p "$off"
  ```

  Afterwards, put the fix in git and restore both: `make k8s-gitops` re-applies the root, which
  re-syncs its children.

## A failed migration (PreSync hook failed)

The sync stops **before** any pod rolls, so the old version keeps serving. `kubectl -n masternova
logs job/db-migrate` has Flyway's error. Fix the migration in a new commit (never edit an
applied one), bump, merge.

## Drain a node (maintenance)

```bash
kubectl drain <node> --ignore-daemonsets --delete-emptydir-data --timeout=5m
```

- api/web: the PDBs evict one pod at a time; they reschedule on other nodes.
- **The database primary:** its eviction is refused at first. CNPG sees the cordon and switches
  over to the standby by itself, then the eviction proceeds. In-flight transactions on the old
  primary fail (`57P01`, a handful of requests): expect it, clients retry.
- The evicted instance stays **Pending** (its volume is on the node) until `kubectl uncordon
  <node>`, then rejoins as a standby. Check with
  `kubectl -n masternova get cluster masternova-db` (`2/2`, "Cluster in healthy state").
