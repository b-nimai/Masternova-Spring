# DevOps study notes

One detailed note per topic. Each is backed by **real files in this repo** (Dockerfiles, compose,
CI, later Helm/Argo CD/Terraform) and by **measurements taken on them**, not generic advice.

Priority marks: **⭐⭐⭐** must know · **⭐⭐** use daily · **⭐** good to know.

| # | Topic | Note | Real files | Roadmap | Status |
|---|---|---|---|---|---|
| 01 | Container images: layers, caching, `dive`, Dockerfile vs Buildpacks, slim runtimes (Alpine chosen over jlink / distroless / chiseled), CVE diet | [01-container-images.md](01-container-images.md) | [`backend/Dockerfile`](../../backend/Dockerfile), [`frontend/Dockerfile`](../../frontend/Dockerfile) | D1.1–D1.2 | ✅ |
| 02 | The JVM in containers: cgroup awareness, `MaxRAMPercentage` + non-heap budget, the Serial-GC surprise, JVM OOM vs kernel OOM kill, Java 25 AOT cache (12.5 → 7 s) | [02-jvm-in-containers.md](02-jvm-in-containers.md) | `backend/Dockerfile` | D1.3 | ✅ |
| 03 | Runtime hardening: memory/swap/CPU/pids limits, restart policies, read-only FS + tmpfs, capabilities, secrets as files (`configtree`), log rotation, graceful stop; the Compose → Kubernetes mapping | [03-compose-hardening.md](03-compose-hardening.md) | `compose.yaml`, `Makefile`, api `application.yaml` | D1.4 | ✅ |
| 04 | CI/CD: GHCR publishing (immutable tags, least-privilege tokens, push-after-scan), tag-driven releases, SBOM + signing, quality gates, branch protection, e2e | [04-ci-cd-pipeline.md](04-ci-cd-pipeline.md) | `.github/workflows/` | D2 | ✅ |

**Try it:** `make images` builds all three images · `make scan` runs Trivy on them.
