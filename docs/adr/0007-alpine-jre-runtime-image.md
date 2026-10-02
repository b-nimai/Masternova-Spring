# ADR-0007 — Alpine Temurin JRE as the runtime base for the Spring Boot images

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai
**Context links:** [`backend/Dockerfile`](../../backend/Dockerfile) · [DevOps note 01 §7](../../patterns/devops/01-container-images.md#7-slim-runtimes-five-bases-measured-) (all measurements) · roadmap D1.2

## Context

The api and worker images used `eclipse-temurin:25-jre`, an Ubuntu 26.04 base:

- 444 MB unpacked, 189 MB compressed, of which only ~79 MB is ours.
- 106 OS packages.
- **41 CVEs** (37 MEDIUM, 4 LOW). None were fixable by us; they're in packages the app never
  uses.

Every package is attack surface and scanner noise, and every MB is pulled on every new node.

## Options measured

The same app was built on each base, then:

1. booted against the compose Postgres + Redis;
2. signup, login and health were called;
3. it was scanned with Trivy (all severities).

| Runtime base | Unpacked | Compressed | CVEs | Shell | Boots? |
|---|---|---|---|---|---|
| `eclipse-temurin:25-jre` (Ubuntu), the old one | 444 MB | 189 MB | 41 (37 M, 4 L) | bash | ✅ |
| **`eclipse-temurin:25-jre-alpine`** | 309 MB | 145 MB | 1 (fixable) → **0** with `apk upgrade` | BusyBox `sh` | ✅ |
| `gcr.io/distroless/java25-debian13:nonroot` | 311 MB | 143 MB | 64, **8 HIGH** (libexpat, libuuid) | none | ✅ |
| `ubuntu/jre:25-26.04_stable` (chiseled) | 281 MB | 147 MB | "0", **but Trivy can't read chisel's manifest**: the OS isn't scanned at all | none | ✅ (but defaults to **root**) |
| `jlink` custom JRE (25 modules) on `distroless/base-debian13` | **176 MB** | **121 MB** | 23 (15 M, 8 L) | none | ❌ at first: `jdeps` missed `jdk.net` (needed by Lettuce/Netty through reflection); ✅ once added |

## Decision

Use **`eclipse-temurin:25-jre-alpine`** for the api and worker:

- `apk upgrade --no-cache` at build time, the same as the web image.
- A non-root `app` user.
- The healthcheck uses BusyBox `wget`, which the base already ships.

Result: api **316 MB unpacked / 148 MB compressed, 0 CVEs at any severity**. The worker is the
same.

## Consequences

**Positive:**

- **41 → 0 CVEs**, so the Trivy gate in CI now means something: any finding is new.
- ~30 % smaller on disk, ~22 % smaller to pull.
- Same vendor (Adoptium) and the same JRE build as before. Dependabot keeps bumping the tag.
- Still has a minimal shell for the healthcheck and for emergency debugging.

**Negative:**

- **musl instead of glibc.** It's fine for a pure-JVM app: we have no JNI, and Netty falls back
  to NIO, as the smoke test showed. Native libraries built for glibc wouldn't load. Revisit if
  one is ever added.
- **`apk upgrade` makes builds depend on the day they run.** We accept that for security fixes,
  as the web image already does.
- **A shell exists.** It's one more tool for an attacker who gets code execution, though far less
  than the Ubuntu image had.

## Alternatives rejected

| Option | Why not |
|---|---|
| **jlink custom JRE** | Smallest by far (121 MB), but the module list is a **whitelist that fails at runtime, in whichever code path first needs a missing module**. We hit it: Redis health failed on `jdk/net/ExtendedSocketOptions`. Every dependency upgrade can need a new module, and only a full boot + traffic test catches it. Worth it at fleet scale with an image smoke test in CI (D2); not yet. |
| distroless `java25` | Bigger CVE count, including 8 HIGH, from the desktop/font libraries the Java image carries. No shell, so the healthcheck would need a separate binary. |
| Ubuntu chiseled `ubuntu/jre` | A good image, but **our scanner can't see inside it**. A green scan we can't trust is worse than a noisy one. It also runs as root unless `USER` is set. Revisit when Trivy supports chisel manifests. |
| Buildpacks (Paketo tiny) | Measured in D1.1: 4 CVEs, no shell, a 1.27 GB builder and 5× slower builds. We keep a Dockerfile we can read line by line. |
| Stay on Ubuntu | 41 known CVEs of pure noise, and a larger pull, for no benefit to a JVM app. |
