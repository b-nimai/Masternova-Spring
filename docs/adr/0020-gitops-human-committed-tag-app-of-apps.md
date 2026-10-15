# ADR-0020 — GitOps by a human-committed image tag; two charts under an Argo CD app-of-apps

**Status:** accepted · **Date:** 2026-10-15 · **Deciders:** Nimai
**Amends:** [ADR-0004](0004-kubernetes-first-deploy.md) §2 ("CI … bumps the tag in `deploy/helm` values")
**Context links:** [DevOps note 06](../../patterns/devops/06-kubernetes.md) §5, §7 · [runbook](../runbooks/deploy-and-rollback.md)

## Context

ADR-0004 chose a local cluster deployed by GitOps: CI publishes images, and git says which one
runs. Building it in D4 hit three constraints:

- **Who writes the tag to git.** ADR-0004 had CI commit the bump. `main` is PR-only (branch
  protection, D2.5), and the owner decided in D2.2 that no bot writes commits. A bot would also
  need write access to the repository.
- **Ordering.** The apps' migration runs as a pre-upgrade hook and needs the database. The
  database is a CNPG `Cluster` that must exist (and be healthy) first.
- **Proof.** CI on `main` had silently failed to publish two phases' images (cancelled runs,
  §5.6 of the note). A tag in git that the registry doesn't have is an outage that GitOps would
  faithfully deploy.

## Decision

1. **A deployment is a commit, by a person.** `make k8s-bump TAG=sha-…` checks that all three
   images exist on GHCR (anonymously, as the cluster will pull them), then rewrites `image.tag` in
   `deploy/helm/masternova/values-local.yaml` and commits it as the user. A PR and a merge deploy
   it. CI never writes to the repository or touches the cluster.
2. **Two charts.** `masternova-infra` (CNPG `Cluster`, Redis, SeaweedFS, Mailpit) and
   `masternova` (api, worker, web, ingress, the `db-migrate` hook). On AWS (D6) the first is
   replaced by managed services.
3. **App-of-apps.** One root Application (`deploy/argocd/root.yaml`, the only object applied by
   hand) syncs both, in sync waves (infra 0, apps 1), with the Application health check restored
   so wave 1 waits for wave 0 to be Healthy.
4. **Fences.** An AppProject limited to this repo, one namespace, one cluster-scoped kind and
   **no Secrets**; the root in a project that may create only Applications; the built-in
   `default` project emptied. The database opts out of prune and delete.

## Consequences

- **Positive:**
  - The history shows who deployed what, as normal commits; `git revert` is the rollback for an
    image.
  - Measured: a pushed bump goes live in about 2 minutes, drift is reverted in 5 s, and the
    cluster was rebuilt from git in under 6 minutes.
  - No credentials outside the cluster: Argo CD pulls.
- **Negative:**
  - A deployment needs a human and a PR, even for a green main build.
  - Secrets are created by hand (`make k8s-secrets`), outside GitOps, until Sealed Secrets or
    External Secrets.

## Alternatives rejected

| Option | Why not |
|---|---|
| CI commits the bump (ADR-0004 as written) | a bot with write access to a PR-only `main`; the owner's no-bot-commits decision |
| CI opens a bump PR | bot-authored commits in the history; still a manual merge |
| Argo CD Image Updater (write-back to the Application, not git) | git no longer says exactly what runs: weaker GitOps |
| One chart for apps + data | the pre-install migration hook runs before the chart's own database exists |
| CI runs `helm upgrade` (push) | cluster credentials in GitHub; can't reach a laptop cluster anyway |
