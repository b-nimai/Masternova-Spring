# Masternova-Spring

An EdTech platform: **Java 25 · Spring Boot 4 · Spring Modulith · Angular 22 · PostgreSQL · Redis · Docker · Kubernetes**.

> **The one sentence:** it's an EdTech platform, but the real project is the backend problem
> underneath it. **How do you sell access to a video, and then make sure only the people who
> paid can watch it**, without ever double-charging anyone, and without melting under load?

Three sub-problems fall out of that:

1. **Get the video ready to stream:** resumable uploads → transcode pipeline → HLS.
2. **Take the money exactly once:** checkout, idempotency keys, transactional outbox, webhook dedupe.
3. **Decide who's allowed to watch:** an entitlement policy chain, cached, with short-lived playback tokens.

This is a ground-up rebuild of the NestJS Masternova in Java, built to practise production OOP,
low-level design patterns, Angular, and a full DevOps chain. Progress: [`ROADMAP.md`](ROADMAP.md).

## Architecture

A **modular monolith plus a separate worker**
([ADR-0001](docs/adr/0001-spring-modulith-modular-monolith.md)):

| Part | What it is |
|---|---|
| `backend/api` | Spring Boot 4 HTTP API. One Spring Modulith module per bounded context; boundaries verified by a test |
| `backend/worker` | Spring Boot 4 background worker: outbox relay, email, transcode pipeline |
| `backend/kernel` | Plain-Java shared kernel: event contracts, `@DesignPattern` |
| `frontend` | Angular 22 SPA (standalone components, signals, Material 3), served by nginx |
| `patterns` | ⭐ Every design pattern used, with a note, the real class, and a runnable lab copy |

More: [`docs/hld/01-architecture.md`](docs/hld/01-architecture.md) · [`docs/api/conventions.md`](docs/api/conventions.md) · [`docs/adr/`](docs/adr/)

## Run it

Prerequisites: JDK 25, Node 22 + pnpm, Docker.

```bash
make up      # postgres, redis, mailpit (S3 storage: opt-in, Phase 7)
make api     # http://localhost:8080/api/v1/meta/ping
make web     # http://localhost:4200
```

Or everything in containers:

```bash
make stack   # http://localhost:8081
```

| Service | URL |
|---|---|
| Web (dev / container) | http://localhost:4200 · http://localhost:8081 |
| API | http://localhost:8080/api/v1 · health at `/actuator/health` |
| Worker health | http://localhost:8090/actuator/health |
| Mailpit (every dev email) | http://localhost:8026 |
| MinIO console | http://localhost:9011 (minioadmin / minioadmin), only with `docker compose --profile media up -d`; MinIO's images are no longer published, so Phase 7 picks a replacement |
| Postgres | `localhost:5433` (masternova / masternova) |

## Test

```bash
make test    # backend unit + Spring Modulith + Testcontainers ITs, pattern lab, frontend lint + Vitest
make scan    # Trivy: HIGH/CRITICAL CVEs in all images
```

## Pattern catalog

Start at [`patterns/README.md`](patterns/README.md). Each pattern links to a note
(intent → Masternova problem → UML → code → when *not* to use it → interview Q&A), the
production class (marked `@DesignPattern`), and a Spring-free runnable copy in `patterns/lab`.
