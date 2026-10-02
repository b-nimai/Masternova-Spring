# ADR-0004 — Local Kubernetes + GitOps first, AWS last and optional

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai

## Context

The DevOps goal is learning: containers, CI/CD, orchestration, observability. A cloud
deployment costs money every hour it runs. Kubernetes is the orchestration skill most Java
backend roles ask about.

## Decision

1. **Containers + compose** from Phase 0: multi-stage, layered, non-root images, scanned by
   Trivy in CI.
2. **Phase D4:** a local **k3d** cluster. Write raw manifests first (to learn the objects),
   then a **Helm** chart. Deployment is **GitOps with Argo CD**: CI publishes images to GHCR
   and bumps the tag in `deploy/helm` values, and Argo CD syncs the cluster from git. This
   also solves "CI can't reach a laptop cluster", because the cluster pulls.
3. **Phase D6 (optional):** Terraform for AWS ECS Fargate + RDS + ElastiCache + S3/CloudFront,
   with GitHub OIDC (no long-lived keys). Spin it up, demo it, then `terraform destroy`.

## Consequences

- K8s, Helm and GitOps are learned for free and can be demoed from a laptop.
- No public URL until D6. A screen recording stands in for the live demo.

## Alternatives rejected

| Option | Why not (now) |
|---|---|
| AWS ECS from the start | Ongoing cost, and it teaches ECS rather than Kubernetes |
| Single VPS + compose | Cheap and real, but skips orchestration entirely |
| Managed K8s (EKS/GKE) | Control-plane cost alone exceeds the budget for a learning project |
