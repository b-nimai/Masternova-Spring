# DevOps study notes

One detailed note per topic. Each is backed by **real files in this repo** (Dockerfiles, compose,
CI, later Helm/Argo CD/Terraform) and by **measurements taken on them**, not generic advice.

Priority marks: **⭐⭐⭐** must know · **⭐⭐** use daily · **⭐** good to know.

| # | Topic | Note | Real files | Roadmap | Status |
|---|---|---|---|---|---|
| 01 | Container images: layers, caching, `dive`, Dockerfile vs Buildpacks, slim runtimes (jlink / distroless / chiseled), CVE diet | [01-container-images.md](01-container-images.md) | [`backend/Dockerfile`](../../backend/Dockerfile), [`frontend/Dockerfile`](../../frontend/Dockerfile) | D1.1–D1.2 | 🔨 |
| 02 | The JVM in containers: memory/CPU limits, `MaxRAMPercentage`, startup (AOT cache) | — | `backend/Dockerfile`, `compose.yaml` | D1.3 | ☐ |
| 03 | Compose hardening: limits, restart policies, read-only FS, secrets via files | — | `compose.yaml` | D1.4 | ☐ |
| 04 | CI/CD: GHCR, semver releases, SBOM, coverage gate, CodeQL, branch protection, e2e | — | `.github/workflows/` | D2 | ☐ |

**Try it:** `make images` builds all three images · `make scan` runs Trivy on them.
