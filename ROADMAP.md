# Masternova-Spring — Roadmap & Tracker

> The file you open at the start of every session to decide what to do next.
> Rules: [`CLAUDE.md`](./CLAUDE.md) · Patterns: [`patterns/README.md`](./patterns/README.md) · Architecture: [`docs/hld/01-architecture.md`](./docs/hld/01-architecture.md) · API rules: [`docs/api/conventions.md`](./docs/api/conventions.md)

**Created:** 2026-10-02 · **Last updated:** 2026-10-02 · **Status:** Phase 1 in progress: 1.1–1.3 ✅. Next: 1.4 (generics).

**Why this project exists:** to rebuild the NestJS Masternova in **Java 25 + Spring Boot 4 + Angular**.
The goals:

- Get fluent in Java.
- See OOP in production-shaped code.
- Make LLD stick by putting each design pattern where a real force justifies it.
- Learn the DevOps chain end to end: containers → CI/CD → Kubernetes → observability → cloud.

---

## 1. How to use this file

**Build order:** vertical slices. Each module phase ships its **backend + its Angular screens**
together ([ADR-0003](docs/adr/0003-vertical-slices.md)). DevOps phases (`D1`–`D6`) are
interleaved where there's something real to ship (see §2 for the order).

**Status legend:** `☐` todo · `🔨` in progress · `✅` done · `⏸` deferred · `✂` cut

### 1.1 Session ritual

1. Open the dashboard (§2) and pick the topmost `☐` task in the active phase.
2. Flip it to `🔨`. If the task introduces a pattern, **read its row in
   [`patterns/README.md`](patterns/README.md) first**.
3. Do the work in the order of §1.2.
4. Run the **`code-review`** skill on the diff, then **`simplify`**.
5. Walk the Definition of Done (§1.4). **All of it.** Then flip to `✅` and fill the Date.
6. Update the dashboard's Done/Spent columns.
7. Commit with conventional commits (`feat(identity): …`). **No `Co-Authored-By`, no AI attribution.**

### 1.2 The order of work for every backend unit

1. **LLD first.** Copy `docs/lld/_TEMPLATE.md`, then fill in §1–§6 (problem, forces, domain,
   class diagram, flow, patterns) *before* writing code.
2. **Interface before implementation.** Follow the package layout in
   [`CLAUDE.md` §3](CLAUDE.md#3-backend-structure--package-by-module-layered-inside):
   - the public module API (`<Module>Api`, events) at the top level;
   - then `web/` + `web/dto/` (request/response records), `application/`, `domain/`,
     `infrastructure/`.

   Add a one-line comment **naming the force** on every abstraction.
3. **Unit-test the pattern with no Spring and no DB**, then the persistence with a
   Testcontainers `*IT`.
4. **Pattern bookkeeping.** Add `@DesignPattern` on the class, write the note in
   `patterns/docs/`, put a simplified copy in `patterns/lab/`, and fill in the catalog row.
   `PatternCatalogIntegrityTest` keeps you honest.
5. **Angular slice.** Write the service (HttpClient), then the component (signals), then the
   route, then specs.
6. **Finish the docs in the same commit:** LLD §7–§11, the API conventions if a rule landed,
   an ADR if a real alternative was rejected.
7. **Update the matching note in `../LLD/`** with this code as its example.

### 1.3 Use the right tool

| When the task is… | Use | Not |
|---|---|---|
| A new Angular component / service / guard / interceptor | `pnpm ng g c|s|guard|interceptor …` | hand-created files |
| An Angular Material component | `MatXModule` import + the docs example | hand-rolled widgets |
| A schema change | a new Flyway file `V<n>__<what>.sql` | `ddl-auto=update`, editing an applied migration |
| Formatting | `make format` (Spotless + Prettier) | arguing with the formatter |
| Reviewing a diff | the **`code-review`** skill | eyeballing it |
| Tidying after a feature | the **`simplify`** skill | leaving it |
| Security-sensitive work (auth, webhooks, tokens) | the **`security-review`** skill | hoping |
| Checking the running app | the **`run`** / **`claude-in-chrome`** skills | assuming it works |
| Writing an LLD/OOP study note | the **`make-note`** skill | improvising the shape |
| Committing to this repo only | the **`push-code`** skill | `git add -A` across repos |

### 1.4 Definition of Done (every module task)

- [ ] LLD written **before** the code; finished (§7–§11) in the same commit as the code
- [ ] Every abstraction has its **force named** in a one-line comment; abstractions without one are deleted
- [ ] Swappable dependencies are **interfaces injected through the constructor** (no field injection, no `new` of collaborators)
- [ ] Data access only through repositories; `@Transactional` only on service methods
- [ ] No service over ~200 lines or 5 public methods
- [ ] Pattern classes unit-tested with **no Spring, no DB**
- [ ] Persistence covered by a Testcontainers `*IT`
- [ ] **Idempotency test** (run it twice, or 50× concurrently) if reachable from a retry, webhook or queue
- [ ] `ModularityTests` green: no module reaches into another's internals
- [ ] `@DesignPattern` + `patterns/docs` note + `patterns/lab` copy + catalog row, for every new pattern
- [ ] Angular: service spec + component spec; lint and format clean
- [ ] `./mvnw verify` and `make test` green locally, CI green on the PR
- [ ] `code-review` + `simplify` run on the diff

### 1.5 The prime directive check

> **Every module must be explainable on a whiteboard in an interview.**

Before marking a module `✅`, say all three out loud:

1. **Named responsibility:** one sentence on what it does and does *not* do.
2. **Named pattern + the force** that justified it, and the alternative rejected.
3. **Named seam:** where a second implementation would plug in.

And the inverse trap: **pattern overuse reads as junior faster than no patterns at all.**
One implementation is not a seam. Use a concrete class until a second implementation is real
or planned.

---

## 2. Progress dashboard

Phases are listed **in the order you do them**.

| # | Phase | Tasks | Done | Est | Spent | Status |
|---|---|---|---|---|---|---|
| 1 | [0 — Foundation](#phase-0--foundation) | 12 | 12 | 16 h | ~7 h | ✅ |
| 2 | [1 — Java + Spring warm-up](#phase-1--java--spring-warm-up) | 11 | 3 | 14 h | ~5 h | 🔨 |
| 3 | [2 — Platform kernel](#phase-2--platform-kernel) | 8 | 0 | 16 h | — | ☐ |
| 4 | [3 — Identity + Angular shell](#phase-3--identity--angular-shell) | 10 | 0 | 26 h | — | ☐ |
| 5 | [D1 — Containerization deep-dive](#phase-d1--containerization-deep-dive) | 4 | 0 | 6 h | — | ☐ |
| 6 | [D2 — CI/CD hardening](#phase-d2--cicd-hardening) | 6 | 0 | 10 h | — | ☐ |
| 7 | [4 — Notification + worker](#phase-4--notification--worker) | 8 | 0 | 14 h | — | ☐ |
| 8 | [5 — Catalog](#phase-5--catalog) | 9 | 0 | 20 h | — | ☐ |
| 9 | [6 — Catalog authoring](#phase-6--catalog-authoring) | 8 | 0 | 22 h | — | ☐ |
| 10 | [7 — Media + transcode pipeline](#phase-7--media--transcode-pipeline) | 10 | 0 | 30 h | — | ☐ |
| 11 | [D3 — Observability](#phase-d3--observability) | 5 | 0 | 12 h | — | ☐ |
| 12 | [8 — Entitlement ⭐](#phase-8--entitlement-) | 7 | 0 | 18 h | — | ☐ |
| 13 | [9 — Commerce](#phase-9--commerce) | 9 | 0 | 28 h | — | ☐ |
| 14 | [D4 — Kubernetes (local) + GitOps](#phase-d4--kubernetes-local--gitops) | 8 | 0 | 20 h | — | ☐ |
| 15 | [10 — Enrollment & progress](#phase-10--enrollment--progress) | 6 | 0 | 14 h | — | ☐ |
| 16 | [11 — Engagement + search](#phase-11--engagement--search-cuttable) *(cuttable)* | 5 | 0 | 20 h | — | ☐ |
| 17 | [D5 — Hardening & proof](#phase-d5--hardening--proof) | 6 | 0 | 16 h | — | ☐ |
| 18 | [D6 — AWS](#phase-d6--aws-optional) *(optional)* | 5 | 0 | 24 h | — | ☐ |
| | **Total** | **137** | **15** | **~326 h** | ~12 h | |

**Pace check:** at ~15 h/week this is about 22 weeks. If time runs short, cut in this order:
D6 → Phase 11 → D5.3/D5.4 → Phase 10's Angular polish.

### Environment (verified 2026-10-02)

| Tool | Version | Notes |
|---|---|---|
| JDK | Temurin 25.0.4 LTS | via SDKMAN: `sdk install java 25.0.4-tem` |
| Maven | 3.9.16 | wrapper only (`backend/mvnw`, `patterns/lab/mvnw`), no global install |
| Spring Boot | 4.1.1 | Spring Framework 7, Spring Security 7, Jackson 3, Modulith 2.1.1 |
| Node / pnpm | 22.23 / 12.8 | pnpm needs `allowBuilds` in `frontend/pnpm-workspace.yaml` |
| Angular | 22.2 | standalone, signals, `@Service()`, Vitest, Material 3 |
| Docker | 29.7.2 | Testcontainers + compose |

---

## Phase 0 — Foundation

**Est 16 h** · Goal: a skeleton where every layer runs (backend, frontend, containers, CI,
docs) before any feature exists.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 0.1 | Toolchain: JDK 25 (SDKMAN), Maven wrapper, Node 22 + pnpm | JDK vs JRE, `JAVA_HOME`, wrapper | — | — | 1 h | ✅ | 2026-10-02 |
| 0.2 | Maven multi-module `backend/` (parent + `kernel` + `api` + `worker`) from Initializr | parent POM, BOMs, reactor builds, starters | — | — | 2 h | ✅ | 2026-10-02 |
| 0.3 | Skeleton: `/api/v1/meta/ping`, ProblemDetail handler, permit-all `SecurityFilterChain`, injected `Clock`, Flyway baseline, virtual threads | `@RestController`, records as DTOs, `@RestControllerAdvice`, `@Bean` | — | — | 2 h | ✅ | 2026-10-02 |
| 0.4 | Tests: `ModularityTests`, `ApiApplicationIT` / `WorkerApplicationIT` (Testcontainers), Spotless, JaCoCo | Surefire vs Failsafe, `@ServiceConnection`, `MockMvcTester` | — | — | 2 h | ✅ | 2026-10-02 |
| 0.5 | `patterns/`: catalog, `@DesignPattern`, integrity tests, note template, Strategy note + lab | annotations + reflection, `sealed`, record patterns | — | **Strategy** (lab) | 2 h | ✅ | 2026-10-02 |
| 0.6 | Angular 22 workspace: Material, angular-eslint, Prettier, Vitest, dev proxy; Home shows "API: UP" | — | standalone components, `@Service()`, `inject()`, `toSignal`, `@switch` / `@let`, lazy routes | — | 2 h | ✅ | 2026-10-02 |
| 0.7 | Dockerfiles: layered Spring Boot image (non-root, bash healthcheck), nginx web image | `jarmode=tools extract --layers` | production build, nginx SPA fallback | — | 1.5 h | ✅ | 2026-10-02 |
| 0.8 | `compose.yaml` (infra + `app` profile, offset ports) + `Makefile` | Boot Docker Compose support | — | — | 1 h | ✅ | 2026-10-02 |
| 0.9 | CI (`.github/workflows/ci.yml`: path-filtered backend · lab · frontend · images + Trivy) + Dependabot | — | — | — | 1 h | ✅ | 2026-10-02 |
| 0.10 | Trivy clean: override Boot-managed Tomcat 11.0.26 / Jackson 3.1.7, `apk upgrade` in nginx | overriding managed versions via properties | — | — | 0.5 h | ✅ | 2026-10-02 |
| 0.11 | Docs: ADR 0001–0004, LLD template, API conventions, HLD architecture | — | — | — | 1 h | ✅ | 2026-10-02 |
| 0.12 | Create `github.com/b-nimai/Masternova-Spring` (public), push, CI green. Branch protection moved to D2.5. | `gh` CLI, OAuth `workflow` scope | — | — | 0.5 h | ✅ | 2026-10-02 |

**Exit check** (all green locally, 2026-10-02):

```
cd backend && ./mvnw verify          # kernel 2 · api 3 unit + 3 IT · worker 1 unit + 1 IT
cd patterns/lab && ./mvnw test       # 5 tests (Strategy)
cd frontend && pnpm lint && pnpm test && pnpm build
make stack                           # 7 services healthy; :8081 → nginx → api
make scan                            # 0 HIGH/CRITICAL fixable CVEs in all 3 images
```

---

## Phase 1 — Java + Spring warm-up

**Est 14 h** · Goal: the Java and Spring fundamentals every later phase leans on, practised
in isolation. Each task produces **study material**: complete, tested code in
`patterns/lab/src/main/java/com/masternova/java/<topic>/`, plus a detailed note in
[`patterns/java/`](patterns/java/README.md) with priority marks (⭐⭐⭐ must know · ⭐⭐ daily · ⭐ good to know),
a code walkthrough, common mistakes, interview Q&A and a 30-second recall. Spring exercises go in the `api` app as small, deletable spikes.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 1.1 | `Money` and `LectureDuration` value objects ([study note](patterns/java/01-records-and-value-objects.md)) | records, compact constructors, immutability, `equals`/`hashCode` contract, `BigDecimal`, `Math.*Exact`, regex, `reduce` | — | **Value Object**: catalog row 18 | 1 h | ✅ | 2026-10-02 |
| 1.2 | `PaymentOutcome`, `CouponRule`, `LectureContent`, `Expr` ([study note](patterns/java/02-sealed-types-and-pattern-matching.md)) | `sealed` / `permits` / `non-sealed`, switch expressions, type/record/nested patterns, guards, `_`, exhaustiveness, dominance | — | sealed+switch vs Strategy vs Visitor | 1 h | ✅ | 2026-10-02 |
| 1.3 | Catalog dataset kata: top courses, revenue by category, instructors by rating ([study note](patterns/java/03-collections-and-streams.md)) | Collections + Map API, HashMap internals, CME, `Comparator` chains, lazy streams, `groupingBy`/`partitioningBy`/`toMap`/`teeing`, gatherers | — | — | 1.5 h | ✅ | 2026-10-02 |
| 1.4 | `Result<T>` / `Page<T>` generics | generics, bounded types, wildcards (PECS), type erasure | — | — | 1 h | ☐ | |
| 1.5 | Exceptions + resources | checked vs unchecked, custom hierarchies, try-with-resources, `Optional` done right | — | — | 1 h | ☐ | |
| 1.6 | OOP drills: refactor an inheritance mess to composition | interfaces vs abstract classes, composition over inheritance, encapsulation (package-private) | — | Strategy vs Template Method | 1.5 h | ☐ | |
| 1.7 | Concurrency lab: race condition → `AtomicLong` / `synchronized` / lock; 10k virtual threads | threads, `ExecutorService`, `CompletableFuture`, virtual threads, structured concurrency | — | — | 2 h | ☐ | |
| 1.8 | Spring IoC spike: `@ConfigurationProperties` record, profiles, bean scopes, constructor injection | ApplicationContext, bean lifecycle, `@Configuration` vs `@Component` | — | — | 1.5 h | ☐ | |
| 1.9 | AOP + proxies: a `@LogExecutionTime` aspect; prove the `@Transactional` self-invocation pitfall in a test | JDK vs CGLIB proxies, `@Aspect` | — | **Proxy**: catalog row 15 | 1.5 h | ☐ | |
| 1.10 | Request lifecycle + JPA spike: filter → interceptor → controller → advice; entity lifecycle, persistence context, an N+1 caught in a test | DispatcherServlet, Bean Validation, Hibernate first-level cache, lazy loading | — | — | 1.5 h | ☐ | |
| 1.11 | Angular essentials playground route | — | `signal` / `computed` / `effect`, `input()` / `output()`, RxJS `map` / `switchMap` / `debounceTime` / `catchError` | — | 1 h | ☐ | |

---

## Phase 2 — Platform kernel

**Est 16 h** · Goal: the plumbing every module relies on (errors, events, outbox,
idempotency), built before any module needs it.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 2.1 | `docs/lld/platform-kernel.md` (§1–§6) | — | — | — | 1 h | ☐ | |
| 2.2 | Domain exception hierarchy → Problem Details with stable `code`s; validation `errors[]` | sealed exception hierarchy, `ProblemDetail` extensions | — | — | 2 h | ☐ | |
| 2.3 | Domain events as records in `kernel`; publish with `ApplicationEventPublisher` | events, `@TransactionalEventListener` phases | — | **Observer**: decoupled side effects | 2 h | ☐ | |
| 2.4 | Transactional outbox: `outbox_message` table (Flyway V2), writer in the same transaction, relay with `FOR UPDATE SKIP LOCKED` | `@Transactional` propagation, native queries, `@Scheduled` | — | **Transactional Outbox**: no dual-write | 4 h | ☐ | |
| 2.5 | Compare with the Spring Modulith event publication registry; **ADR-0005** (keep hand-rolled or switch) | Modulith events, `@ApplicationModuleListener` | — | — | 1 h | ☐ | |
| 2.6 | `Idempotency-Key` filter: stored request hash + response, 400/409/422 rules | `OncePerRequestFilter`, `ContentCachingResponseWrapper` | — | Chain of Responsibility (filter) | 3 h | ☐ | |
| 2.7 | Repository + Unit of Work made explicit: domain repository interfaces over Spring Data; `@Transactional` as UoW | Spring Data JPA, derived queries, transactions | — | **Repository + UoW**: catalog row 16 | 1 h | ☐ | |
| 2.8 | Proof: 50 concurrent identical requests → 1 effect (IT); `@ApplicationModuleTest` for `platform` | `CountDownLatch`, virtual-thread executors in tests | — | — | 2 h | ☐ | |

---

## Phase 3 — Identity + Angular shell

**Est 26 h** · Goal: real authentication end to end, and the Angular app shell every later
screen lives in.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 3.1 | `docs/lld/identity.md` + ADR (refresh rotation + reuse detection over stateless JWT) | — | — | — | 1.5 h | ☐ | |
| 3.2 | `User` / `Session` / `RefreshToken` entities, Flyway V3, `citext` email, password hashing | JPA mapping, enums, `PasswordEncoder` (Argon2/BCrypt) | — | **Strategy** (`PasswordEncoder` is one) | 2.5 h | ☐ | |
| 3.3 | Signup + email verification token; `UserRegistered` event → outbox | Bean Validation, events | — | Observer | 2 h | ☐ | |
| 3.4 | Login: JWT access token (Resource Server, HMAC) + rotating refresh token in an httpOnly cookie; reuse detection revokes the family | Spring Security 7 filter chain, `JwtEncoder`/`Decoder`, cookies | — | **Chain of Responsibility** (filter chain) | 5 h | ☐ | |
| 3.5 | RBAC: `LEARNER` / `INSTRUCTOR` / `ADMIN`, `@PreAuthorize`, method security | `@EnableMethodSecurity`, SpEL | — | — | 2 h | ☐ | |
| 3.6 | Google sign-in (optional) as a second `AuthenticationProvider` | OAuth2 client | — | **Strategy** (auth methods): catalog row 1 | 2 h | ☐ | |
| 3.7 | Angular app shell: Material toolbar + sidenav, responsive layout, lazy feature routes | — | layout, `MatSidenav`, `BreakpointObserver`, `loadChildren` | — | 2 h | ☐ | |
| 3.8 | Signup / login / verify pages | — | **typed reactive forms**, custom validators, error mapping from Problem Details | — | 3 h | ☐ | |
| 3.9 | Auth store with signals, functional interceptor (attach token, single-flight refresh on 401), `CanMatchFn` guards, role-based UI | — | `HttpInterceptorFn`, `shareReplay`, guards, signal stores | — | 4 h | ☐ | |
| 3.10 | Tests: `@WebMvcTest` + `spring-security-test`, reuse-detection IT, interceptor + guard specs | `@WithMockUser`, `jwt()` post-processor | `HttpTestingController` | — | 2 h | ☐ | |

---

## Phase D1 — Containerization deep-dive

**Est 6 h** · Goal: understand *why* the images look the way they do, and make them smaller,
safer and faster to start.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D1.1 | Inspect layers (`docker history`, `dive`); compare with Buildpacks `./mvnw spring-boot:build-image` | layer caching, build context, Buildpacks vs Dockerfile | 1.5 h | ☐ | |
| D1.2 | Slim the runtime: `jlink` custom JRE vs distroless vs Ubuntu chiseled; compare size + Trivy results (today: api ≈ 630 MB) | minimal base images, attack surface | 2 h | ☐ | |
| D1.3 | JVM in containers: memory/CPU limits, `MaxRAMPercentage`, Java 25 AOT cache for startup time | container-aware JVM, startup vs throughput | 1.5 h | ☐ | |
| D1.4 | Compose hardening: resource limits, restart policies, read-only FS, secrets via files | runtime security | 1 h | ☐ | |

---

## Phase D2 — CI/CD hardening

**Est 10 h** · Goal: from "CI runs tests" to a delivery pipeline that produces signed,
versioned, scanned artifacts.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D2.1 | Publish images to **GHCR** on `main` (tags: git SHA + semver) with `GITHUB_TOKEN` | registries, immutable tags | 2 h | ☐ | |
| D2.2 | Releases: conventional commits → release-please (changelog, version bump, tag) | semantic versioning, release automation | 1.5 h | ☐ | |
| D2.3 | Supply chain: CycloneDX SBOM (Maven plugin + image SBOM), keyless **cosign** signing | SBOM, provenance, signing | 2 h | ☐ | |
| D2.4 | Quality gates: JaCoCo coverage threshold, CodeQL (Java + TS), Dependabot auto-merge for patches | static analysis, gates | 1.5 h | ☐ | |
| D2.5 | Branch protection, required checks, PR template, CODEOWNERS | trunk-based workflow | 0.5 h | ☐ | |
| D2.6 | Playwright e2e smoke against the compose stack in CI (sign up → log in → see dashboard) | e2e in CI, service containers | 2.5 h | ☐ | |

---

## Phase 4 — Notification + worker

**Est 14 h** · Goal: the worker becomes real: it relays the outbox and sends email reliably.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 4.1 | `docs/lld/notification.md` | — | — | — | 1 h | ☐ | |
| 4.2 | Worker outbox relay: claim batches with `SKIP LOCKED`, dispatch to a handler registry | `@Scheduled`, `Map<String, Handler>` from injected `List` | — | **Observer** + **Factory Method / Registry** | 3 h | ☐ | |
| 4.3 | Consumer idempotency: `processed_event` table; handlers safe to run twice | unique constraints as locks | — | — | 1.5 h | ☐ | |
| 4.4 | Email templates (Thymeleaf): one base class, one subclass per email | Thymeleaf, abstract classes | — | **Template Method** | 2 h | ☐ | |
| 4.5 | `MailProvider`: SMTP (Mailpit) adapter, Resend adapter behind a property | `@ConditionalOnProperty`, `JavaMailSender` | — | **Adapter** | 2 h | ☐ | |
| 4.6 | Suppression list, preferences, HMAC unsubscribe link | `Mac` / HMAC-SHA256 | — | — | 1.5 h | ☐ | |
| 4.7 | Angular: notification preferences page | — | `MatSlideToggle`, optimistic UI | — | 1.5 h | ☐ | |
| 4.8 | Tests: relay crash + redelivery IT, handler run-twice test, Mailpit assertion | Testcontainers `GenericContainer` (Mailpit) | — | — | 1.5 h | ☐ | |

---

## Phase 5 — Catalog

**Est 20 h** · Goal: courses that can be browsed, filtered and paged at scale.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 5.1 | `docs/lld/catalog.md` + ADR (keyset over OFFSET) | — | — | — | 1.5 h | ☐ | |
| 5.2 | JPA model: `Course` / `Section` / `Lecture` / `Category`; fetch strategies; N+1 test with Hibernate statistics | associations, `@EntityGraph`, `JOIN FETCH` | — | — | 3 h | ☐ | |
| 5.3 | `Money` embeddable / converter, minor units | `@Embeddable`, `AttributeConverter` | — | Value Object | 1 h | ☐ | |
| 5.4 | Composable search filters | JPA Criteria API, `Specification<T>` | — | **Specification** | 2.5 h | ☐ | |
| 5.5 | Keyset pagination with an opaque cursor | `Window` / `ScrollPosition` or hand-rolled | — | — | 2 h | ☐ | |
| 5.6 | Course duplication (deep copy, new ids, draft state) | copy constructors vs `clone()` | — | **Prototype** | 2 h | ☐ | |
| 5.7 | Test data builders for every aggregate | fluent APIs | — | **Builder** | 1.5 h | ☐ | |
| 5.8 | Angular: catalog page (filters ↔ URL query params, infinite scroll with cursor) + course detail page | — | router query params, `withComponentInputBinding`, `@defer`, `CdkVirtualScrollViewport` | — | 5 h | ☐ | |
| 5.9 | `docs/db/indexes.md` with `EXPLAIN ANALYZE` evidence for every list query | — | — | — | 1.5 h | ☐ | |

---

## Phase 6 — Catalog authoring

**Est 22 h** · Goal: instructors build courses through a wizard, with undo and safe
concurrent edits.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 6.1 | `docs/lld/catalog-authoring.md` + ADR (optimistic concurrency) | — | — | — | 1.5 h | ☐ | |
| 6.2 | Course lifecycle Draft → InReview → Published → Archived | `sealed interface CourseState`, exhaustive `switch` | — | **State** | 3 h | ☐ | |
| 6.3 | Publish gate: composed rules returning *coded problems* → 422 | composition of predicates | — | **Specification** | 2 h | ☐ | |
| 6.4 | Curriculum edits as commands (add/move/rename/delete section/lecture) with apply + invert | sealed records, Jackson `@JsonTypeInfo` polymorphism | — | **Command** | 4 h | ☐ | |
| 6.5 | Undo/redo: snapshot stack vs inverse commands; build both, keep one, record why | deep copies, `Deque` | — | **Memento** | 2.5 h | ☐ | |
| 6.6 | `@Version` optimistic locking → 409 Problem Detail with versions | JPA `@Version`, `OptimisticLockException` | — | — | 1.5 h | ☐ | |
| 6.7 | Angular: instructor wizard (stepper, typed forms, debounced autosave) | — | `MatStepper`, `FormGroup<T>`, `debounceTime` + `switchMap` | — | 4 h | ☐ | |
| 6.8 | Angular: curriculum editor (drag-drop, undo/redo, 409 conflict dialog) | — | CDK `DragDrop`, `MatDialog`, keyboard shortcuts | — | 3.5 h | ☐ | |

---

## Phase 7 — Media + transcode pipeline

**Est 30 h** · Goal: upload → playable HLS in under 5 minutes, surviving crashes without
duplicates.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 7.1 | `docs/lld/media.md` + `video-pipeline.md` + ADRs (HLS, provider truth) | — | — | — | 2 h | ☐ | |
| 7.2 | `StorageClient` over AWS SDK v2 (MinIO in dev): presign, multipart, list parts | AWS SDK v2, `S3Presigner` | — | **Adapter** | 2.5 h | ☐ | |
| 7.3 | Upload sessions: plan of ≤100 presigned parts, resume = ask the provider; `UploadSession` lifecycle | — | — | **State** | 3 h | ☐ | |
| 7.4 | Worker job DAG: probe → transcode rungs (fan-out) → package HLS → poster/sprite | abstract base processor, registry from `List<JobProcessor>` | — | **Template Method** + **Factory Method** | 4 h | ☐ | |
| 7.5 | ffmpeg via `ProcessBuilder`; command builder; ABR ladder profiles | processes, streams, timeouts | — | **Builder** + **Strategy** | 3 h | ☐ | |
| 7.6 | Retries with backoff, dead-letter + replay endpoint, orphan sweeper (`@Scheduled` + ShedLock) | Spring Retry / Resilience4j, distributed locks | — | — | 3 h | ☐ | |
| 7.7 | Progress over SSE | `SseEmitter`, async request handling | — | — | 1.5 h | ☐ | |
| 7.8 | Angular: chunked uploader (parallel parts, retry, resume) + live progress | — | `EventSource` → signal, `HttpClient` with `reportProgress` | — | 5 h | ☐ | |
| 7.9 | Angular: HLS player component (hls.js) | — | lifecycle hooks, `ElementRef`, `afterNextRender` | — | 2 h | ☐ | |
| 7.10 | Proof: SIGKILL the worker mid-job → no duplicate renditions (IT) | deterministic output keys, upserts | — | — | 4 h | ☐ | |

---

## Phase D3 — Observability

**Est 12 h** · Goal: see what the system is doing. Metrics, traces and logs, correlated,
with alerts that mean something.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D3.1 | Micrometer → Prometheus (`/actuator/prometheus`), custom metrics (orders, transcode duration, outbox lag) | RED/USE metrics, cardinality | 2.5 h | ☐ | |
| D3.2 | Grafana dashboards **provisioned as code** (JVM, HTTP, Hikari, pipeline); compose `observability` profile | dashboards-as-code | 2.5 h | ☐ | |
| D3.3 | OpenTelemetry traces → Tempo; trace id in every log line; api → worker propagation through the outbox | distributed tracing, context propagation | 3 h | ☐ | |
| D3.4 | Logs → Loki (Grafana Alloy); jump log ↔ trace | centralized logging | 1.5 h | ☐ | |
| D3.5 | SLOs + burn-rate alert rules, Alertmanager → Mailpit; first runbooks in `docs/runbooks/` | SLO/SLI, error budgets | 2.5 h | ☐ | |

---

## Phase 8 — Entitlement ⭐

**Est 18 h** · Goal: decide who may watch, correctly, fast, and explainably.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 8.1 | `docs/lld/entitlement-engine.md` + ADRs (cache the row not the decision; playback token) | — | — | — | 1.5 h | ☐ | |
| 8.2 | Policy chain: each policy returns ALLOW / DENY / ABSTAIN, explicit DENY wins; policies are ordered beans | `@Order`, `List<Policy>` injection, sealed `Decision` | — | **Chain of Responsibility** + **Strategy** | 4 h | ☐ | |
| 8.3 | Cached entitlement repository (Redis) + invalidation on grant/revoke events | Spring Cache, `RedisTemplate`, cache-aside | — | **Decorator** | 3 h | ☐ | |
| 8.4 | `EntitlementService` as the module's single entry point; hook into `@PreAuthorize` via a custom bean | facade API design | — | **Facade** | 2 h | ☐ | |
| 8.5 | 5-minute HMAC playback token (user + lecture + IP) + manifest endpoint | `Mac`, constant-time compare | — | — | 2.5 h | ☐ | |
| 8.6 | Angular: locked/unlocked lecture UI; denial reason → the right CTA; player guard | — | discriminated unions in templates, `@switch` | — | 3 h | ☐ | |
| 8.7 | Tests: parameterized policy matrix, cache invalidation IT | `@ParameterizedTest`, `@MethodSource` | — | — | 2 h | ☐ | |

---

## Phase 9 — Commerce

**Est 28 h** · Goal: take the money exactly once, even when the provider retries,
reorders or races the redirect.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 9.1 | `docs/lld/order-state-machine.md` + ADRs (webhook dedupe; grant in the order transaction) | — | — | — | 2 h | ☐ | |
| 9.2 | Cart + pricing: coupon types and eligibility rules | `BigDecimal` pitfalls vs minor units | — | **Strategy** (coupon types) + **Specification** (eligibility) | 3 h | ☐ | |
| 9.3 | Order state machine: forward-only transitions, out-of-order safe | sealed states, `EnumSet` transition tables | — | **State** | 3 h | ☐ | |
| 9.4 | `PaymentGateway` + Razorpay adapter (test mode), signature verification | `RestClient`, HMAC | — | **Strategy** + **Adapter** | 3.5 h | ☐ | |
| 9.5 | `CheckoutService`: Idempotency-Key, order + entitlement in one transaction | transaction boundaries | — | **Facade** | 3 h | ☐ | |
| 9.6 | Webhook endpoint: raw-body signature, claim-before-process on provider event id | raw `byte[]` bodies, unique-constraint claims | — | — | 3 h | ☐ | |
| 9.7 | Refunds → `EntitlementRevoked` event | events | — | Observer | 2 h | ☐ | |
| 9.8 | Proof: **50 concurrent copies of one webhook → exactly 1 enrollment** (IT) | concurrency testing | — | — | 3 h | ☐ | |
| 9.9 | Angular: cart, checkout (Razorpay Checkout), payment result, order history | — | third-party script loading, `NgZone`-free callbacks | — | 5.5 h | ☐ | |

---

## Phase D4 — Kubernetes (local) + GitOps

**Est 20 h** · Goal: run the whole system on Kubernetes, deployed by git commits
([ADR-0004](docs/adr/0004-kubernetes-first-deploy.md)).

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D4.1 | k3d cluster + local registry; `kubectl` fundamentals (contexts, namespaces, describe/logs/exec) | cluster anatomy | 2 h | ☐ | |
| D4.2 | Raw manifests first: Deployment, Service, ConfigMap, Secret, probes from actuator liveness/readiness, requests/limits | core objects, probes, QoS | 3 h | ☐ | |
| D4.3 | Ingress (Traefik): `/` → web, `/api` → api; TLS via cert-manager (self-signed) | ingress, TLS termination | 2 h | ☐ | |
| D4.4 | Postgres via **CloudNativePG** operator, Redis via chart; Flyway as a pre-deploy Job | operators, stateful workloads, Jobs | 3 h | ☐ | |
| D4.5 | Helm chart in `deploy/helm/masternova` (values per env); `helm lint` + `helm template` in CI | templating, release management | 3 h | ☐ | |
| D4.6 | HPA on api (CPU); **KEDA** on worker (outbox lag metric) | autoscaling | 2 h | ☐ | |
| D4.7 | **Argo CD**: Application for the chart; CI bumps the image tag in git → Argo syncs | GitOps, pull-based CD | 3 h | ☐ | |
| D4.8 | Zero-downtime drill: rolling update + expand-contract migration under k6 load | rollout strategies, PodDisruptionBudget | 2 h | ☐ | |

---

## Phase 10 — Enrollment & progress

**Est 14 h** · Goal: learners see their courses and resume exactly where they stopped,
without hammering the database.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 10.1 | `docs/lld/progress.md` + ADR (write-back, accepted 30 s loss) | — | — | — | 1 h | ☐ | |
| 10.2 | Enrollment from `OrderPaid` / free enrollment | `@ApplicationModuleListener` | — | Observer | 2 h | ☐ | |
| 10.3 | Heartbeats → Redis write-back buffer, flushed every 30 s | Redis hashes, `@Scheduled` | — | **Decorator** (write-back) | 3 h | ☐ | |
| 10.4 | Monotonic `maxPositionSeconds`, % complete rollups | SQL `GREATEST`, upserts | — | — | 2 h | ☐ | |
| 10.5 | Angular: My Learning dashboard, resume playback, `sendBeacon` on unload | — | `DestroyRef`, `fromEvent`, `navigator.sendBeacon` | — | 4 h | ☐ | |
| 10.6 | Tests: flush-on-crash IT, monotonicity property test | — | — | — | 2 h | ☐ | |

---

## Phase 11 — Engagement + search *(cuttable)*

**Est 20 h**

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 11.1 | Reviews + incremental rating aggregates + nightly reconciliation | `@Scheduled`, aggregate maintenance | — | Observer | 4 h | ☐ | |
| 11.2 | Lecture Q&A threads | recursive data, pagination | — | — | 3 h | ☐ | |
| 11.3 | Postgres full-text search (`tsvector`, GIN) | native queries | — | — | 3 h | ☐ | |
| 11.4 | Semantic search with pgvector (embeddings via Spring AI); hybrid ranking | Spring AI, vectors | — | **Strategy** (rankers) | 5 h | ☐ | |
| 11.5 | Angular: reviews, Q&A, search box with typeahead | — | `switchMap` cancellation, `MatAutocomplete` | — | 5 h | ☐ | |

---

## Phase D5 — Hardening & proof

**Est 16 h** · Goal: numbers and drills that prove the design, plus the story for interviews.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D5.1 | k6 load test vs SLOs (p95 < 300 ms); `docs/hld/03-capacity.md` | load testing, capacity | 3 h | ☐ | |
| D5.2 | `EXPLAIN ANALYZE` tuning pass; Hikari pool sizing | query plans, connection pools | 2.5 h | ☐ | |
| D5.3 | Chaos drills: kill a worker mid-transcode, kill an api pod under load, CNPG failover | resilience testing | 3 h | ☐ | |
| D5.4 | Backup + point-in-time restore drill (CNPG → MinIO) | RPO/RTO | 2 h | ☐ | |
| D5.5 | Security: OWASP ZAP baseline in CI, `security-review` skill pass, secrets scan | DAST, secret scanning | 2 h | ☐ | |
| D5.6 | README polish, architecture diagram, 3-minute demo video | storytelling | 3.5 h | ☐ | |

---

## Phase D6 — AWS *(optional)*

**Est 24 h** · Goal: the same system on real cloud infrastructure, created and destroyed with
Terraform. **Run it only while you demo; set a budget alarm first.**

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D6.1 | Terraform fundamentals; remote state (S3 + lock); modules | IaC, state, plan/apply | 4 h | ☐ | |
| D6.2 | GitHub OIDC → AWS IAM role (no long-lived keys) | federated identity | 2 h | ☐ | |
| D6.3 | VPC, ECR, ECS Fargate services + ALB | networking, container orchestration on AWS | 7 h | ☐ | |
| D6.4 | RDS Postgres, ElastiCache Redis, S3 + CloudFront signed cookies | managed data services, CDN | 7 h | ☐ | |
| D6.5 | Budget alarm, deploy pipeline, demo recording, `terraform destroy` | cost control | 4 h | ☐ | |

---

## 3. Skills checklists

Tick these as they're used *for real* in the project, not just read about.

### 3.1 Java

- [x] records, compact constructors · [x] sealed interfaces + pattern-matching `switch` · [x] annotations + reflection
- [ ] generics (bounded, wildcards) · [x] Streams + Collectors · [ ] `Optional` · [ ] exception hierarchies
- [x] `equals`/`hashCode`/immutability · [ ] interfaces vs abstract classes · [ ] package-private encapsulation
- [ ] threads, executors, `CompletableFuture` · [x] virtual threads (config) · [ ] locks / atomics · [ ] `ProcessBuilder`

### 3.2 Spring

- [x] Boot auto-configuration + starters · [x] `@RestController` / records as DTOs · [x] ProblemDetail · [x] Actuator
- [ ] DI deep-dive (scopes, profiles, `@ConfigurationProperties`) · [ ] AOP + proxies · [ ] Bean Validation
- [ ] Spring Data JPA / Hibernate · [x] Flyway · [ ] transactions + propagation · [ ] Spring Security 7 + JWT
- [ ] Spring Modulith events · [x] Modulith verification · [ ] Spring Cache + Redis · [ ] `@Scheduled` / ShedLock · [ ] SSE
- [x] Testcontainers + `@ServiceConnection` · [x] `MockMvcTester` · [ ] `@WebMvcTest` / `@DataJpaTest` slices

### 3.3 Angular

- [x] standalone components · [x] `@Service()` + `inject()` · [x] `toSignal` · [x] `@switch` / `@let` control flow · [x] lazy routes
- [ ] `signal` / `computed` / `effect` · [ ] `input()` / `output()` · [ ] typed reactive forms · [ ] interceptors · [ ] guards
- [ ] RxJS operators in anger · [ ] Material (stepper, dialog, table) · [ ] CDK drag-drop / virtual scroll · [ ] `@defer`
- [x] Vitest + `HttpTestingController` · [ ] Playwright e2e

### 3.4 DevOps

- [x] multi-stage Dockerfile · [x] layered jars · [x] non-root + healthcheck · [x] compose profiles + healthchecks
- [x] GitHub Actions (path filters, caching, matrix) · [x] Trivy · [x] Dependabot · [ ] GHCR · [ ] SBOM + cosign · [ ] release automation
- [ ] Prometheus · [ ] Grafana · [ ] OpenTelemetry · [ ] Loki · [ ] SLO alerts
- [ ] kubectl · [ ] Deployments / Services / Ingress · [ ] probes + limits · [ ] Helm · [ ] operators · [ ] HPA / KEDA · [ ] Argo CD
- [ ] Terraform · [ ] OIDC to AWS · [ ] ECS / RDS / ElastiCache / CloudFront

---

## 4. Interview-evidence checklist

Each line is a sentence you can say *and* a file or test you can show.

- [ ] "Module boundaries are enforced by a test." → `ModularityTests` + generated module diagram
- [ ] "Every pattern is cataloged and verified." → `patterns/README.md` + `PatternCatalogIntegrityTest`
- [ ] "Webhooks are exactly-once in effect." → the 50× concurrent webhook IT (9.8)
- [ ] "Transcoding survives a crash without duplicates." → the SIGKILL IT (7.10)
- [ ] "Access decisions are explainable." → the policy chain with reason codes (8.2) + parameterized matrix test
- [ ] "I can show p95 under load." → k6 report (D5.1)
- [ ] "Deploys are git commits." → Argo CD sync history (D4.7)
- [ ] "Images are scanned, signed and have SBOMs." → CI run (D2.3)
- [ ] "I can trace one request across api → outbox → worker." → Tempo screenshot (D3.3)

---

## 5. Deviations from the plan

| Deviation | Why |
|---|---|
| One `backend/Dockerfile` with `--build-arg APP=api\|worker` instead of one Dockerfile per app | Both apps build identically; one file can't drift from the other |
| `spring-modulith-starter-jpa` removed from the Initializr selection | Its event-publication table would need a hand-written migration now. Phase 2 hand-rolls the outbox first, then compares (2.5). |
| google-java-format pinned to 1.35.0 | 1.36+ pulls in a commonmark dependency that Spotless 3.10 fails to load |
| Healthcheck via bash `/dev/tcp` instead of curl | The Temurin JRE image ships no curl/wget; installing them only adds CVEs |
| First CI run failed: `aquasecurity/trivy-action@0.33.1` (tags now carry a `v` prefix) | Fixed to `@v0.36.0` in 94f94b0. Lesson: pin action versions you have verified exist. |
| App packages are `com.masternova.api.*` / `com.masternova.worker.*` (not `com.masternova.*`) | Keeps Modulith from treating the shared `kernel` package as an api module |
