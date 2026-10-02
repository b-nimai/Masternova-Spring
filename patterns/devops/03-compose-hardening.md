# DevOps 03: Hardening the Container Runtime (Compose)

> **One-liner:** an image decides *what* runs; the runtime settings decide *what it may do*. Give
> every container a **memory/CPU/pids budget**, a **restart policy**, **rotated logs**, a
> **read-only filesystem**, **no Linux capabilities** it doesn't need, and **secrets as files**,
> never env vars. Each control is one line of YAML. Each maps one-to-one onto a Kubernetes field
> you'll write in D4.

**Roadmap:** D1.4 · **Last updated:** 2026-10-02
**Real files:** [`compose.yaml`](../../compose.yaml) (`x-jvm-app`, `x-logging`, `secrets:`), [`Makefile`](../../Makefile) (`make secrets`), [`backend/api/src/main/resources/application.yaml`](../../backend/api/src/main/resources/application.yaml) (`spring.config.import: optional:configtree:/run/secrets/`)
**Try it:** `make stack`, then the checks in §8.

**Priority marks:** ⭐⭐⭐ must know (interviews, incidents) · ⭐⭐ use daily · ⭐ good to know.

| # | Section | Priority |
|---|---|---|
| 1 | [The controls at a glance](#1-the-controls-at-a-glance-) | ⭐⭐⭐ |
| 2 | [Resource limits: memory, swap, CPU, pids](#2-resource-limits-memory-swap-cpu-pids-) | ⭐⭐⭐ |
| 3 | [Restart policies](#3-restart-policies-) | ⭐⭐ |
| 4 | [Read-only root filesystem](#4-read-only-root-filesystem-) | ⭐⭐ |
| 5 | [Linux capabilities and `no-new-privileges`](#5-linux-capabilities-and-no-new-privileges-) | ⭐⭐⭐ |
| 6 | [Secrets as files, not env vars](#6-secrets-as-files-not-env-vars-) | ⭐⭐⭐ |
| 7 | [Logs and graceful shutdown](#7-logs-and-graceful-shutdown-) | ⭐⭐ |
| 8 | [Verifying it: the checks we ran](#8-verifying-it-the-checks-we-ran-) | ⭐⭐ |
| 9 | [From Compose to Kubernetes](#9-from-compose-to-kubernetes-) | ⭐⭐⭐ |
| 10 | [Common mistakes](#10-common-mistakes-) | ⭐⭐⭐ |
| 11 | [Interview Q&A](#11-interview-qa-) | ⭐⭐⭐ |
| 12 | [30-second recall](#12-30-second-recall) | ⭐⭐⭐ |

---

## 1. The controls at a glance ⭐⭐⭐

The JVM apps share one YAML anchor, so the policy is written once:

```yaml
x-jvm-app: &jvm-app
  restart: unless-stopped
  read_only: true
  tmpfs: [/tmp:size=64m]
  cap_drop: [ALL]
  security_opt: ['no-new-privileges:true']
  stop_grace_period: 40s
  deploy:
    resources:
      limits: { memory: 1g, cpus: '2', pids: 512 }
  memswap_limit: 1g
  logging: *logging            # json-file, 10 MB × 3

services:
  api:
    <<: *jvm-app               # ⭐ YAML merge key: inherit the whole block
    secrets: [{ source: jwt_access_secret, target: JWT_ACCESS_SECRET }]
```

| Control | api / worker | web (nginx) | infra (postgres, redis, minio, mailpit) | Stops |
|---|---|---|---|---|
| memory / CPU / pids limits | 1 GiB / 2 / 512 | 128 MiB / 0.5 / 128 | — (dev convenience) | a leak or a fork bomb taking the host down |
| no swap (`memswap_limit` = memory) | ✅ | ✅ | — | a JVM crawling in swap instead of failing fast |
| restart policy | `unless-stopped` | `unless-stopped` | `unless-stopped` (`minio-init`: `no`) | a crash staying down |
| read-only root FS + tmpfs | ✅ `/tmp` | ✅ `/var/run`, `/var/cache/nginx`, `/tmp` | — (databases write by design) | an attacker or bug modifying the app or dropping tools |
| capabilities | **none** | `CHOWN SETUID SETGID NET_BIND_SERVICE` | defaults | privilege escalation, raw sockets, `chown` of files |
| `no-new-privileges` | ✅ | ✅ | — | setuid binaries raising privileges |
| secrets as files | `JWT_ACCESS_SECRET` | — | — | secrets leaking via `inspect`, `/proc`, crash dumps |
| log rotation | ✅ | ✅ | ✅ | a chatty container filling the disk |
| `stop_grace_period` | 40 s | default 10 s | default | in-flight requests cut off on deploy |

---

## 2. Resource limits: memory, swap, CPU, pids ⭐⭐⭐

```yaml
deploy:
  resources:
    limits:
      memory: 1g     # cgroup memory.max: the JVM sizes its heap from it (75 % → 768 MB)
      cpus: '2'      # cgroup cpu.max quota: the JVM sees 2 processors
      pids: 512      # cgroup pids.max: threads count too
memswap_limit: 1g    # memory + swap; equal to memory means NO swap
```

- **Memory.** Without a limit, each JVM sizes itself from the **host**: we measured a 5.8 GB max
  heap, and the api idling at **510 MiB**. With the 1 GiB limit it idles at **281 MiB**. Why 1 GiB:
  the api's working set is ~350 MB, and 75 % heap + ~250 MB non-heap must fit (note 02 §2).
- **Swap.** Docker's default lets a container use as much swap again as its memory limit. For a
  JVM that's the worst outcome: GC walks the whole heap, so a heap partly in swap turns every GC
  into disk I/O. The service stays "up", just 100× slower. **No swap** means it fails fast and
  the restart policy brings it back.
- **CPU.** `cpus: '2'` is a quota (2 CPU-seconds per second), not pinning. It is also what makes
  the JVM's ergonomics deterministic: 2 processors, whatever the host has.
- **pids.** Threads are pids in Linux. Our JVMs run ~25 (virtual threads are not OS threads). 512
  stops a thread leak or a fork bomb long before it can exhaust the host's pid table.

Infra containers have no limits here on purpose: it's a dev machine, and Postgres tuning is its
own topic. In Kubernetes (D4) every pod gets requests and limits.

---

## 3. Restart policies ⭐⭐

| Policy | Restarts after a crash | After `docker stop` / `kill` | After a daemon/host reboot |
|---|---|---|---|
| `no` (default) | ❌ | ❌ | ❌ |
| `on-failure[:N]` | ✅ only on a non-zero exit (max N times) | ❌ | ✅ if it was running |
| `always` | ✅ | ❌ (until the daemon restarts, then ✅) | ✅ |
| ⭐ `unless-stopped` | ✅ | ❌ and stays stopped | ✅ unless you'd stopped it |

**What we observed:**

| Action | Result |
|---|---|
| `docker kill --signal=KILL worker` | **not restarted** (`restarts=0`): Docker treats `kill` as a *manual* stop |
| `docker compose exec worker kill -TERM 1` (the process exits by itself, as in a crash) | **restarted** (`restarts=1`), after Spring's graceful shutdown |

That's the design: a policy reacts to the **process dying**, never to an operator's decision.
It pairs with `-XX:+ExitOnOutOfMemoryError` (note 02 §4): an OOM becomes a clean exit, and the
policy turns that into a fresh instance.

`minio-init` is a one-shot job, so `restart: 'no'`. With `unless-stopped` it would loop forever.

---

## 4. Read-only root filesystem ⭐⭐

```yaml
read_only: true          # the container's root filesystem is mounted read-only
tmpfs: [/tmp:size=64m]   # in-memory, writable, size-capped, gone on restart
```

Proof:

```text
$ docker compose exec api touch /app/x   →  touch: cannot touch '/app/x': Read-only file system
$ docker compose exec api touch /tmp/x   →  ok
```

**Why:**

- An attacker with code execution can't replace a JAR, add a cron job or download tools into the
  image's filesystem.
- A bug can't silently fill the container's writable layer.
- It **documents** exactly where the app writes.

**Finding the writable paths:** start with `read_only: true`, read the errors, and add `tmpfs`
for each path that really needs writing:

| App | Writes to | Why |
|---|---|---|
| Spring Boot / Tomcat | `/tmp` | Tomcat's work dir, multipart uploads, the JVM's `hsperfdata` |
| nginx | `/var/run` (pid), `/var/cache/nginx` (proxy temp), `/tmp` | runtime state |

The AOT cache (`/app/app.aot`) is only **read** (memory-mapped), so it works on a read-only
filesystem.

---

## 5. Linux capabilities and `no-new-privileges` ⭐⭐⭐

Root's power in Linux is split into ~40 **capabilities**: `NET_BIND_SERVICE` (bind ports < 1024),
`CHOWN`, `SETUID`, `NET_RAW` (raw sockets, ping), `SYS_ADMIN` (almost everything), … Docker gives
every container a default set of ~14, even when the process runs as non-root.

```yaml
cap_drop: [ALL]                           # api, worker: a non-root JVM on :8080 needs none
security_opt: ['no-new-privileges:true']  # no setuid escalation, whatever binaries exist
```

nginx is the instructive case. Its master process starts as **root** to bind :80, and `chown`s its
temp dirs (fresh, root-owned tmpfs). Then it switches its workers to the `nginx` user. So it keeps
exactly four:

```yaml
cap_drop: [ALL]
cap_add: [CHOWN, SETUID, SETGID, NET_BIND_SERVICE]
```

What we read from `/proc/1/status`:

| Container | `CapEff` | Decoded |
|---|---|---|
| api | `0000000000000000` | nothing |
| worker | `0000000000000000` | nothing |
| web | `00000000000004c1` | bits 0, 6, 7, 10 = CHOWN, SETGID, SETUID, NET_BIND_SERVICE |

The alternative for the web is an unprivileged nginx listening on :8080 (e.g. the
`nginxinc/nginx-unprivileged` image). Then it needs no capabilities either. That's worth doing
when it moves to Kubernetes, where the Service maps port 80 anyway.

---

## 6. Secrets as files, not env vars ⭐⭐⭐

**Why env vars leak:**

| Leak path | Example |
|---|---|
| `docker inspect` / `kubectl describe` | anyone with read access to the runtime sees every value |
| `/proc/<pid>/environ` | readable by the same user, and by debuggers |
| child processes | inherit the whole environment |
| crash reports, `env` dumps, Actuator `/env` | log aggregators end up holding them |

**How it works here:**

```yaml
# compose.yaml
secrets:
  jwt_access_secret:
    file: ./secrets/jwt_access_secret      # gitignored; `make secrets` creates 48 random bytes
services:
  api:
    secrets:
      - source: jwt_access_secret
        target: JWT_ACCESS_SECRET          # → the file /run/secrets/JWT_ACCESS_SECRET
```

```yaml
# backend/api/src/main/resources/application.yaml
spring:
  config:
    import: optional:configtree:/run/secrets/   # ⭐ each FILE becomes a property named after it
masternova:
  identity:
    jwt-secret: ${JWT_ACCESS_SECRET:dev-only-secret-…}   # unchanged: resolves from the file now
```

**Spring's `configtree`** turns a directory of files into properties: the file name is the key
and the content is the value (a trailing newline is trimmed). `optional:` means a missing
directory is fine, so running from the IDE still uses the dev default. **The application code
didn't change at all.** The same placeholder now finds the value in a file instead of the
environment.

**Proof it's the file and not a fallback:** we decoded a real access token from login and
recomputed its HMAC-SHA256 signature in Python:

```text
signature matches secrets/jwt_access_secret: True
signature matches the dev default:          False
env vars containing JWT in `docker inspect`: 0
```

**Kubernetes (D4)** mounts a `Secret` as files in exactly the same way (`volumeMounts` →
`/run/secrets/...`), so this `configtree` line works unchanged there.

**Local-dev caveats:**

- Compose (outside Swarm) bind-mounts the file as-is, so it must be readable by the container user.
  `make secrets` uses mode 644 in the gitignored `secrets/` folder. In Kubernetes,
  `defaultMode: 0400` + `fsGroup` restrict it properly.
- The **database password** stays an env var locally. The host-run api (`make api`) and the
  compose Postgres share the dev password `masternova`. In D4 it moves to a Secret file the same
  way. Postgres itself supports `POSTGRES_PASSWORD_FILE`.

---

## 7. Logs and graceful shutdown ⭐⭐

**Log rotation:** Docker's default `json-file` driver **never rotates**:

```yaml
x-logging: &logging
  driver: json-file
  options: { max-size: 10m, max-file: '3' }   # ≤ 30 MB per container, oldest file dropped
```

In Kubernetes the kubelet rotates container logs and a collector ships them (D3: Loki).

**Graceful shutdown:** `docker compose stop`, a deploy, and a Kubernetes rollout all send
**SIGTERM**, wait for the grace period, then send SIGKILL.

- Spring (`server.shutdown: graceful`) stops accepting new requests and gives in-flight ones up
  to 30 s (`spring.lifecycle.timeout-per-shutdown-phase`).
- Compose's default grace period is **10 s**, which would cut that off.
- So `stop_grace_period: 40s` (30 s + margin). Kubernetes's equivalent is
  `terminationGracePeriodSeconds`.

We saw it in the restart test: `GracefulShutdown` log lines, then a clean exit.

This works only because the image uses the **exec-form** `ENTRYPOINT`: Java is PID 1 and gets
the SIGTERM (note 01 §2).

---

## 8. Verifying it: the checks we ran ⭐⭐

| Check | Command | Result |
|---|---|---|
| secret not in env | `docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' <api> \| grep -c JWT` | `0` |
| secret mounted | `docker compose exec api ls -l /run/secrets/` | `JWT_ACCESS_SECRET` (64 bytes) |
| secret actually used | recompute the JWT's HMAC with the file's bytes | matches; the dev default doesn't |
| read-only | `docker compose exec api touch /app/x` | `Read-only file system` |
| tmpfs writable | `docker compose exec api touch /tmp/x` | ok |
| capabilities | `docker compose exec api grep CapEff /proc/1/status` | api/worker `0`, web `0x4c1` |
| limits | `docker inspect -f '{{.HostConfig.Memory}} {{.HostConfig.MemorySwap}} {{.HostConfig.NanoCpus}} {{.HostConfig.PidsLimit}}' <api>` | `1073741824 1073741824 2000000000 512` |
| usage under limits | `docker stats --no-stream` | api 281 MiB / 1 GiB, 27 pids; web 13 MiB / 128 MiB |
| restart on crash | `docker compose exec worker kill -TERM 1` | `restarts=1`, healthy again |
| no restart on manual kill | `docker kill worker` | stays `exited` |
| whole stack | `make stack` | all 7 services healthy; signup through nginx → 201 |

---

## 9. From Compose to Kubernetes ⭐⭐⭐

Every control here has a direct Kubernetes field. D4 writes these:

| Compose | Kubernetes |
|---|---|
| `deploy.resources.limits.memory/cpus` | `resources.limits.memory/cpu` (+ `requests` for scheduling) |
| `pids` | the kubelet's `podPidsLimit` (node-level) |
| `memswap_limit` = memory | no swap: the default on most nodes |
| `restart: unless-stopped` | `restartPolicy: Always` (Deployments) + liveness probes |
| `read_only: true` + `tmpfs` | `securityContext.readOnlyRootFilesystem: true` + an `emptyDir` (`medium: Memory`) volume |
| `cap_drop: [ALL]` / `cap_add` | `securityContext.capabilities.drop: [ALL]` / `add` |
| `no-new-privileges` | `allowPrivilegeEscalation: false` |
| image `USER app` | `runAsNonRoot: true` (+ `runAsUser`) |
| `secrets:` → `/run/secrets/*` | a `Secret` mounted as a volume, same `configtree` import |
| `stop_grace_period` | `terminationGracePeriodSeconds` (+ a `preStop` sleep for endpoint removal) |
| `HEALTHCHECK` | `livenessProbe` / `readinessProbe` / `startupProbe` on the Actuator groups |
| log options | the kubelet's log rotation + a log collector |

---

## 10. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | § |
|---|---|---|
| no memory limit on a JVM container | always set one; the heap is derived from it | 2 |
| a memory limit, but swap still allowed | `memswap_limit` = the memory limit | 2 |
| `restart: always` on a one-shot init job | `restart: 'no'` | 3 |
| expecting `docker kill` to test the restart policy | make the process exit by itself | 3 |
| a writable root FS "because something writes somewhere" | `read_only` + a tmpfs for each real write path | 4 |
| a non-root user but default capabilities | `cap_drop: [ALL]`, add back only what's proven necessary | 5 |
| secrets in `environment:` | files + `configtree` (or the platform's secret store) | 6 |
| committing the secret file | a gitignored folder, generated per machine | 6 |
| default 10 s stop timeout with a 30 s graceful shutdown | `stop_grace_period` > the app's drain time | 7 |
| unrotated `json-file` logs | `max-size` / `max-file` | 7 |
| assuming a control works | verify each one (§8): a setting you never tested is a hope | 8 |

---

## 11. Interview Q&A ⭐⭐⭐

**Q1. How do you harden a container at runtime?**
A non-root user and a minimal image (build time). Then memory/CPU/pids limits, a read-only root
filesystem with tmpfs for the real write paths, `cap_drop: ALL` plus only the proven-needed
capabilities, `no-new-privileges`, secrets as files, rotated logs, and a restart policy. In
Kubernetes, these become `securityContext` and `resources`.

**Q2. Why shouldn't secrets be environment variables?**
They're visible through `docker inspect`/`kubectl describe`, `/proc/<pid>/environ`, child
processes and crash dumps. Mount them as files (Docker/Kubernetes secrets) with tight permissions.
Spring reads them with `spring.config.import: configtree:`, with no code change.

**Q3. What are Linux capabilities?**
Root's privileges split into units (`NET_BIND_SERVICE`, `CHOWN`, `SYS_ADMIN`, …). Containers get a
default subset even as non-root. Drop all, and add back only what's needed: nginx needs four to
bind :80 and drop to its worker user; our JVMs need none.

**Q4. `restart: always` vs `unless-stopped` vs `on-failure`?**
`on-failure` restarts only on a non-zero exit. `always` and `unless-stopped` restart on any exit;
`unless-stopped` also respects a manual stop across daemon restarts. None of them restart after a
manual stop or `docker kill`.

**Q5. Why disable swap for a JVM container?**
GC touches the whole heap. If part of it is swapped out, every collection becomes disk I/O, and
the service degrades massively instead of failing. Fail fast and restart instead.

**Q6. The container stops, but in-flight requests fail. Why?**
The stop grace period is shorter than the app's drain time (Docker's default is 10 s, Spring's
graceful phase 30 s), or the entrypoint is shell-form, so the JVM never gets the SIGTERM.

---

## 12. 30-second recall

- **Budget:** memory 1 GiB (the JVM derives 768 MB of heap), `memswap_limit` = memory (no swap),
  `cpus: 2`, `pids: 512`.
- **Restart:** `unless-stopped` reacts to the process dying, never to `stop`/`kill`. One-shot jobs:
  `no`.
- **Filesystem:** `read_only: true` + a tmpfs for the real write paths (`/tmp` for Spring;
  `/var/run`, `/var/cache/nginx` for nginx). The AOT cache is read-only and fine.
- **Privileges:** `cap_drop: ALL` + `no-new-privileges`. The JVMs need 0 capabilities; nginx
  needs 4 (CHOWN, SETUID, SETGID, NET_BIND_SERVICE).
- **Secrets:**
  - Files at `/run/secrets/*`, read via `spring.config.import: optional:configtree:/run/secrets/`.
  - No code change; proven by recomputing the JWT signature.
  - `inspect` shows nothing.
- **Logs:** `json-file` 10 MB × 3.
- **Shutdown:** `stop_grace_period` 40 s > Spring's 30 s drain; exec-form entrypoint.
- **Kubernetes:** each line maps to `resources` / `securityContext` / Secret volumes /
  `terminationGracePeriodSeconds` (D4).
