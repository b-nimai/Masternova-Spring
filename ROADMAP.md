# Masternova-Spring — Roadmap & Tracker

> The file you open at the start of every session to decide what to do next.
> Rules: [`CLAUDE.md`](./CLAUDE.md) · Patterns: [`patterns/README.md`](./patterns/README.md) · Architecture: [`docs/hld/01-architecture.md`](./docs/hld/01-architecture.md) · API rules: [`docs/api/conventions.md`](./docs/api/conventions.md)

**Created:** 2026-10-02 · **Last updated:** 2026-10-13 · **Status:** Phase 1 ✅ (PR #10) · Phase 2 ✅ (PR #11) · Phase 3 ✅ (PR #15; 3.6 Google sign-in ⏸ deferred) · Phase D1 ✅ (PR #16) · Phase D2 ✅ (PR #17) · Phase 4 ✅ (PR #18) · Phase 5 ✅ (PR #19) · Phase 6 ✅ (PR #20) · Phase 7 ✅ (PR #22) · Phase D3 ✅.

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
7. **Commit on the phase branch** (`phase-N/<name>`) with a conventional commit (`feat(identity): …`).
   **Don't push yet.** **No `Co-Authored-By`, no AI attribution.**
8. **When the whole phase is done:** push the branch, open a PR to `main`, wait for green CI,
   merge (merge commit, so the per-task commits are kept), **delete the branch** (remote + local, [CLAUDE.md §7](CLAUDE.md#7-commits)), then branch off `main` for the next phase.

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
| 2 | [1 — Java + Spring warm-up](#phase-1--java--spring-warm-up) | 11 | 11 | 14 h | ~17 h | ✅ |
| 3 | [2 — Platform kernel](#phase-2--platform-kernel) | 8 | 8 | 16 h | ~15 h | ✅ |
| 4 | [3 — Identity + Angular shell](#phase-3--identity--angular-shell) | 10 | 9 | 26 h | ~24 h | ✅ |
| 5 | [D1 — Containerization deep-dive](#phase-d1--containerization-deep-dive) | 4 | 4 | 6 h | ~7 h | ✅ |
| 6 | [D2 — CI/CD hardening](#phase-d2--cicd-hardening) | 6 | 6 | 10 h | ~8.5 h | ✅ |
| 7 | [4 — Notification + worker](#phase-4--notification--worker) | 8 | 8 | 14 h | ~14 h | ✅ |
| 8 | [5 — Catalog](#phase-5--catalog) | 9 | 9 | 20 h | ~20.5 h | ✅ |
| 9 | [6 — Catalog authoring](#phase-6--catalog-authoring) | 8 | 8 | 22 h | ~21 h | ✅ |
| 10 | [7 — Media + transcode pipeline](#phase-7--media--transcode-pipeline) | 10 | 10 | 30 h | ~34 h | ✅ |
| 11 | [D3 — Observability](#phase-d3--observability) | 5 | 5 | 12 h | ~12 h | ✅ |
| 12 | [8 — Entitlement ⭐](#phase-8--entitlement-) | 7 | 0 | 18 h | — | ☐ |
| 13 | [9 — Commerce](#phase-9--commerce) | 9 | 0 | 28 h | — | ☐ |
| 14 | [D4 — Kubernetes (local) + GitOps](#phase-d4--kubernetes-local--gitops) | 8 | 0 | 20 h | — | ☐ |
| 15 | [10 — Enrollment & progress](#phase-10--enrollment--progress) | 6 | 0 | 14 h | — | ☐ |
| 16 | [11 — Engagement + search](#phase-11--engagement--search-cuttable) *(cuttable)* | 5 | 0 | 20 h | — | ☐ |
| 17 | [D5 — Hardening & proof](#phase-d5--hardening--proof) | 6 | 0 | 16 h | — | ☐ |
| 18 | [D6 — AWS](#phase-d6--aws-optional) *(optional)* | 5 | 0 | 24 h | — | ☐ |
| | **Total** | **137** | **90** | **~326 h** | ~180 h | |

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
| 1.4 | `Result<T>`, `Page<T>`, `Registry<K, V>`, `Ranking`, `TypedSettings` ([study note](patterns/java/04-generics.md)) | generics, invariance, bounded types, wildcards (PECS), type erasure, raw types, type tokens | — | generic Strategy registry | 1 h | ✅ | 2026-10-02 |
| 1.5 | Domain exceptions, CSV importer, resources, `Optional` lookups, retry ([study note](patterns/java/05-exceptions-and-optional.md)) | checked vs unchecked, sealed exception hierarchy → HTTP, try-with-resources + suppressed, `finally` traps, `InterruptedException`, `Optional` done right | — | prototype of Phase 2 error model | 1 h | ✅ | 2026-10-02 |
| 1.6 | Fragile base class, Channel decorators, email Template Method, rich `Cart`, dispatch traps ([study note](patterns/java/06-oop-composition-over-inheritance.md)) | interfaces vs abstract classes, composition over inheritance, encapsulation as invariants, overriding vs overloading, SOLID in code | — | **Decorator**, **Template Method**; Strategy vs Decorator vs Template Method | 1.5 h | ✅ | 2026-10-02 |
| 1.7 | Counters, cohort seats (CAS), 50× webhook idempotency, quote fan-out, 10k virtual threads, `ScopedValue`, outbox producer–consumer ([study note](patterns/java/07-concurrency-and-virtual-threads.md)) | races, JMM/`volatile`, atomics/locks, `ConcurrentHashMap`, executors, `CompletableFuture`, virtual threads, `ScopedValue`, back-pressure, deadlock | — | — | 2 h | ✅ | 2026-10-02 |
| 1.8 | Real `MasternovaProperties` + IoC learning tests with `ApplicationContextRunner` ([study note](patterns/java/08-spring-ioc-and-di.md)) | DI resolution (`@Primary`/`@Qualifier`/`List`/`ObjectProvider`), scopes + prototype trap, lifecycle, full vs lite `@Configuration`, profiles, conditions, auto-config back-off, `@ConfigurationProperties` | — | — | 1.5 h | ✅ | 2026-10-02 |
| 1.9 | AOP + proxy learning tests (recording transaction manager), Proxy pattern note + lab ([study note](patterns/java/09-spring-aop-and-proxies.md), [pattern](patterns/docs/15-proxy.md)) | JDK vs CGLIB proxies, `@Aspect`/`@Around`, self-invocation, `@Transactional` rollback rules & propagation, `TransactionTemplate` | — | **Proxy**: catalog row 15 | 1.5 h | ✅ | 2026-10-02 |
| 1.10 | Request lifecycle learning test + JPA fundamentals IT on real Postgres ([study note](patterns/java/10-request-lifecycle-and-jpa.md)) | DispatcherServlet order, filter vs interceptor vs advice, Bean Validation, persistence context, entity states, dirty checking, lazy loading, **N+1 measured (11 → 1)**, Spring Data | — | — | 1.5 h | ✅ | 2026-10-02 |
| 1.11 | Angular essentials `/playground` route ([study note](patterns/angular/01-angular-essentials.md)) | — | `signal`/`computed`/`effect`, signal store, `input()`/`output()`/`model()`, control flow, RxJS typeahead (`debounceTime`/`distinctUntilChanged`/`switchMap`/`catchError`), marble tests, zoneless | — | 1 h | ✅ | 2026-10-02 |

---

## Phase 2 — Platform kernel

**Est 16 h** · Goal: the plumbing every module relies on (errors, events, outbox,
idempotency), built before any module needs it.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 2.1 | [`docs/lld/platform-kernel.md`](docs/lld/platform-kernel.md) (§1–§9 drafted; §10–§11 after 2.8) | — | — | — | 1 h | ✅ | 2026-10-02 |
| 2.2 | Sealed `DomainException` kinds → Problem Details with stable `code`s, `type` URIs, `errors[]`; codes for Spring's own errors; no-leak 500 | sealed exception hierarchy, `ProblemDetail` extensions, `ResponseEntityExceptionHandler` hooks | — | — | 2 h | ✅ | 2026-10-02 |
| 2.3 | `DomainEvent` (kernel) + `EventPublisher` (`MANDATORY` tx) + observer-timing tests; Observer pattern note + lab ([pattern](patterns/docs/07-observer.md)) | events, `@TransactionalEventListener` phases, `Propagation.MANDATORY`, thread-bound transactions | — | **Observer**: decoupled side effects (catalog ✅) | 2 h | ✅ | 2026-10-02 |
| 2.4 | Transactional outbox: `outbox_message` (Flyway V2, partial index), writer in the caller's tx, relay claiming with `FOR UPDATE SKIP LOCKED` + lease, backoff → DEAD; IT on Postgres incl. 4 concurrent relays ([pattern](patterns/docs/17-transactional-outbox.md)) | propagation `MANDATORY`, `JdbcClient` + `UPDATE … RETURNING`, `@Scheduled`, typed relay config | — | **Transactional Outbox** (catalog ✅) | 4 h | ✅ | 2026-10-02 |
| 2.5 | [ADR-0005](docs/adr/0005-hand-rolled-outbox-over-modulith-registry.md): keep the hand-rolled outbox (cross-process, no broker); `@TransactionalEventListener` for non-critical in-process reactions | Modulith events, `@ApplicationModuleListener`, externalization | — | — | 1 h | ✅ | 2026-10-02 |
| 2.6 | `Idempotency-Key` filter (V3): atomic claim (`ON CONFLICT DO NOTHING` + conditional takeovers), sealed `Claim` outcomes, byte-for-byte replay, 5xx releases the key, `@IdempotencyKeyRequired` interceptor, expiry cleanup; HTTP-level IT | `OncePerRequestFilter`, request-body caching, `ContentCachingResponseWrapper`, SHA-256 hashing | — | Chain of Responsibility (filter) | 3 h | ✅ | 2026-10-02 |
| 2.7 | Repository + Unit of Work made explicit: platform repositories behind interfaces; pattern note + hand-built UoW lab (identity map, change tracking, all-or-nothing commit) ([pattern](patterns/docs/16-repository-unit-of-work.md)) | repositories vs DAOs, `@Transactional` + persistence context as the UoW | — | **Repository + UoW** (catalog ✅) | 1 h | ✅ | 2026-10-02 |
| 2.8 | ⭐ Proof: 50 concurrent identical requests (real HTTP) → **1** execution; `PlatformModuleIT` (`@ApplicationModuleTest`) boots the module standalone; LLD §10–§11 finished | `CountDownLatch`, virtual-thread HTTP clients in tests, Modulith module tests | — | — | 2 h | ✅ | 2026-10-02 |

---

## Phase 3 — Identity + Angular shell

**Est 26 h** · Goal: real authentication end to end, and the Angular app shell every later
screen lives in.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 3.1 | [`docs/lld/identity.md`](docs/lld/identity.md) + [ADR-0006](docs/adr/0006-rotating-refresh-tokens-over-stateless-jwt.md) (rotating refresh tokens + reuse detection) | — | — | — | 1.5 h | ✅ | 2026-10-02 |
| 3.2 | `User` aggregate, `AuthSession`, `RefreshToken`, `VerificationToken` (V4), `Email` value object, atomic token `consume`, delegating `PasswordEncoder` | JPA mapping, `@ElementCollection`, enums, Spring Data `@Modifying` queries, `SecureRandom` tokens, schema validation | — | **Strategy** (`DelegatingPasswordEncoder`) | 2.5 h | ✅ | 2026-10-02 |
| 3.3 | Signup (race-safe `EMAIL_TAKEN`) + single-use email verification; `UserRegistered` through the outbox; `IdentityProperties`; dev-only link logger | Bean Validation, value-object validation, `DataIntegrityViolationException` translation, dirty checking, module-owned `@ConfigurationProperties` | — | Observer (via outbox) | 2 h | ✅ | 2026-10-02 |
| 3.4 | Login (timing-safe, no account probing) → 15-min HS256 JWT + rotating httpOnly/SameSite=Strict refresh cookie; reuse revokes the family (committed via `noRollbackFor`); logout; `/me`; deny-by-default chain fed by modules' `PublicEndpoints`; 401/403 as Problem Details; 6th error kind | Spring Security 7, resource server, `JwtEncoder`/`JwtDecoder` + validators, `ResponseCookie`, `@CookieValue`, `noRollbackFor` | — | **Chain of Responsibility** (filter chain) | 5 h | ✅ | 2026-10-02 |
| 3.5 | RBAC: `CurrentUser` argument resolver; admin role management with `@PreAuthorize` on the service; self-demotion guard; first-admin bootstrap runner; `AccessDeniedException` → 403 problem | `@EnableMethodSecurity`, SpEL, `HandlerMethodArgumentResolver`, `ApplicationRunner` | — | — | 2 h | ✅ | 2026-10-02 |
| 3.6 | Google sign-in (optional) as a second `AuthenticationProvider` | OAuth2 client | — | **Strategy** (auth methods): catalog row 1 | 2 h | ⏸ | deferred: needs real Google OAuth client credentials (Google Cloud console) — pick up when they exist |
| 3.7 | Angular app shell: sticky toolbar + sidenav (overlay on handsets, side on desktop) driven by `NAV_ITEMS`; lazy routes | — | `MatSidenav`, `BreakpointObserver` → `toSignal`, `routerLinkActive`, faking breakpoints in tests | — | 2 h | ✅ | 2026-10-02 |
| 3.8 | Signup / login / verify-email / account / admin pages (lazy routes behind `guestGuard` / `authGuard` / `roleGuard('ADMIN')`), open-redirect-safe `returnUrl`, `AdminApi` | — | **typed reactive forms** (`NonNullableFormBuilder`), custom + cross-field validators, error mapping from Problem Details onto controls, query-param input binding | — | 3 h | ✅ | 2026-10-02 |
| 3.9 | `AuthStore` (token in memory only, `restoreSession` on app init, **single-flight** refresh), functional interceptor (Bearer + refresh-once-and-retry, never on auth endpoints), `authGuard`/`roleGuard`/`guestGuard` (`CanMatch`), role-filtered nav + user menu; Problem helpers *(done before 3.8)* | — | `HttpInterceptorFn`, `shareReplay` single-flight, `provideAppInitializer`, `CanMatchFn` + `UrlTree`, signal stores | — | 4 h | ✅ | 2026-10-02 |
| 3.10 | Tests: `@WebMvcTest` slice with the real security chain (`AdminUserControllerTest`), method security in a tiny context (`UserAdminServiceSecurityTest`), reuse-detection + two-tab ITs, interceptor/guard/store/page specs; identity LLD §10–11; API conventions §13; [Angular note 02](patterns/angular/02-routing-guards-interceptors-forms.md) | `@WebMvcTest` + `excludeFilters`, `@MockitoBean`, `jwt()` post-processor, `@WithMockUser`, `@SpringJUnitConfig` | `HttpTestingController`, `runInInjectionContext`, `setInput`, DOM-driven form tests | — | 2.5 h | ✅ | 2026-10-02 |

---

## Phase D1 — Containerization deep-dive

**Est 6 h** · Goal: understand *why* the images look the way they do, and make them smaller,
safer and faster to start.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D1.1 | Inspect layers (`docker history`, `dive`); compare with Buildpacks `./mvnw spring-boot:build-image`. Findings: a code change ships 1 layer (0.65 MB) after moving the healthcheck above the app layers; Buildpacks tiny 41 → 4 CVEs. [DevOps note 01](patterns/devops/01-container-images.md) | layer caching, build context, Buildpacks vs Dockerfile, reproducible builds | 1.5 h | ✅ | 2026-10-02 |
| D1.2 | Slim the runtime: measured Ubuntu / Alpine / distroless / chiseled / `jlink` (boot + smoke test + Trivy each) → **Alpine Temurin JRE** ([ADR-0007](docs/adr/0007-alpine-jre-runtime-image.md)): api 444 → 316 MB unpacked, 189 → 148 MB compressed, **41 → 0 CVEs**; BusyBox `wget` healthcheck | minimal base images, attack surface, jlink/jdeps pitfalls, scanner blind spots (chisel) | 2 h | ✅ | 2026-10-02 |
| D1.3 | JVM in containers: measured heap/GC/CPU ergonomics per limit (Serial GC below 2 CPUs / ~1.8 GB), the api's ~350 MB working set, JVM OOM (exit 3) vs kernel OOM kill (137); **Java 25 AOT cache** in the image: 12.5 → 7 s to ready, −60 MB. [DevOps note 02](patterns/devops/02-jvm-in-containers.md) | container-aware JVM, cgroups, GC ergonomics, OOM killer, AOT cache, startup vs image size | 1.5 h | ✅ | 2026-10-02 |
| D1.4 | Compose hardening: 1 GiB / 2 CPU / 512 pids limits with no swap, `unless-stopped`, read-only root + tmpfs, `cap_drop: ALL` (nginx keeps 4), `no-new-privileges`, JWT secret as a file via Spring `configtree` (proven by recomputing the token signature), log rotation, 40 s graceful stop; each control verified. [DevOps note 03](patterns/devops/03-compose-hardening.md) | runtime security, cgroups, capabilities, secret handling | 1 h | ✅ | 2026-10-02 |

---

## Phase D2 — CI/CD hardening

**Est 10 h** · Goal: from "CI runs tests" to a delivery pipeline that produces signed,
versioned, scanned artifacts.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D2.1 | Publish images to **GHCR** on `main` as `:sha-<commit>` (immutable) + `:main`, pushed only after the Trivy scan passes, with a job-scoped `packages: write` `GITHUB_TOKEN`; OCI labels; actionlint-clean. [DevOps note 04](patterns/devops/04-ci-cd-pipeline.md) | registries, immutable tags, least privilege | 1 h | ✅ | 2026-10-02 |
| D2.2 | Releases, **tag-driven** (owner's decision: no bot commits, so no release-please): `make release VERSION=X.Y.Z` pushes an annotated tag; `release.yml` guards that it's on `main`, **promotes** CI's scanned `:sha-` image to `:X.Y.Z`/`:X.Y` with `imagetools` (builds only if missing), and creates a GitHub Release with generated notes. Images report their commit at `/actuator/info` (`GIT_SHA` arg after the AOT layer) | semantic versioning, promote-don't-rebuild, release automation without bot commits | 1.5 h | ✅ | 2026-10-02 |
| D2.3 | Supply chain: CycloneDX SBOM inside each jar (Boot-managed plugin, jars stay byte-reproducible), BuildKit SBOM + SLSA provenance attestations on pushed images, Syft SBOMs as release assets, **keyless cosign** signing of the digest via GitHub OIDC (verification demonstrated) | SBOM, provenance, signing, Sigstore (Fulcio/Rekor) | 2 h | ✅ | 2026-10-02 |
| D2.4 | Quality gates: JaCoCo **merged** unit + IT coverage (api 27.5 % unit-only → 91 % merged) with per-module ratchet floors that fail `verify`; CodeQL (Java + TS, `build-mode: none`, weekly); actionlint job for workflows; Dependabot **without** auto-merge (owner's decision: re-applied as the owner's commits) | static analysis, gates, coverage ratchets | 1.5 h | ✅ | 2026-10-02 |
| D2.5 | Branch protection on `main` (owner's decision: PR + green checks, 0 approvals, admins included, no force-push) requiring one aggregate **`ci-ok`** job (`if: always()`, every job success-or-skipped) + CodeQL; PR template; CODEOWNERS | trunk-based workflow, required checks with path filters | 0.5 h | ✅ | 2026-10-02 |
| D2.6 | Playwright e2e (`e2e/`, Chromium) against the full compose stack: sign up → log in → **reload restores the session from the httpOnly cookie** → log out; generic bad-password message; learner kept out of `/admin`; open-redirect blocked. In CI: images loaded from the `images` job's cache, `compose up --wait`, traces + container logs on failure; feeds `ci-ok`. `make e2e` locally (4 tests, ~7 s) | e2e in CI, test pyramid, browser-only guarantees | 2 h | ✅ | 2026-10-02 |

---

## Phase 4 — Notification + worker

**Est 14 h** · Goal: the worker becomes real: it relays the outbox and sends email reliably.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 4.1 | [`docs/lld/notification.md`](docs/lld/notification.md) (send side in the worker, consent side in the api, per-email delivery state machine, categories, HMAC unsubscribe) + [ADR-0008](docs/adr/0008-shared-messaging-module.md) (shared `messaging` module) | — | — | — | 1 h | ✅ | 2026-10-02 |
| 4.2 | Shared `messaging` module (ADR-0008): outbox writer + `SKIP LOCKED` relay + handler registry move out of the api (git history kept, `V2` migration moved with the same checksum), wired by Boot **auto-configuration** (writer always; relay + schedule only with `relay-enabled`: the worker). ITs moved with it; `ApplicationContextRunner` tests prove the conditions; coverage 96 % / 86 %. [Pattern note 09](patterns/docs/09-factory-method-registry.md) + lab | `@AutoConfiguration` + `AutoConfiguration.imports`, `@ConditionalOnBooleanProperty` / `OnMissingBean`, `ApplicationContextRunner`, `Map<String, Handler>` from injected `List` | — | **Observer** + **Factory Method / Registry** | 3 h | ✅ | 2026-10-02 |
| 4.3 | Consumer idempotency for email: `V5__notification.sql` (`email_delivery`, `email_suppression`, `notification_preference`), `NotificationCategory` in the kernel, and the `email_delivery` claim as ONE `INSERT … ON CONFLICT DO UPDATE … WHERE` (re-claim only `FAILED` or a `SENDING` older than the lease) with sealed `Claim` outcomes; IT: **20 concurrent claims → 1 sender**, stale lease taken over, citext pitfall (`citext = text` compares case-sensitively → `CAST`). Worker ITs run the api's real migrations. *(Generic `processed_event` deferred to Phase 9: LLD §6)* | unique constraints as locks, `INSERT … ON CONFLICT … WHERE … RETURNING`, sealed results | — | Repository | 1.5 h | ✅ | 2026-10-02 |
| 4.4 | Email templates (Thymeleaf): `EmailTemplate<P>` with a **final** `render` skeleton (unsubscribe-link invariant → model → HTML via a shared table/inline-style `layout.html` → plaintext twin → footer hook); `VerifyEmailTemplate` (mandatory) + `WelcomeEmailTemplate` (PRODUCT_NEWS) with typed payload records; one email engine with HTML + TEXT resolvers; unit tests without Spring (escaping, invariant, `final`). [Pattern note 06](patterns/docs/06-template-method.md) | Thymeleaf HTML/TEXT modes + fragments, abstract classes, generics, `final` methods | — | **Template Method** | 2 h | ✅ | 2026-10-02 |
| 4.5 | `MailProvider` adapters: SMTP (`JavaMailSender`, 5xx `SMTPAddressFailedException` → permanent) + Resend (`RestClient`, 422 → permanent) behind `masternova.notification.provider`; `NotificationService` (render → claim → send → SENT / FAILED+rethrow / BOUNCED); `SendVerificationEmail` handler with a consumer-owned event view. Tests: fakes for the pipeline, `MockRestServiceServer`, Mailpit container IT; **live: signup → email in Mailpit in 3 s**. Pattern note [12 Adapter](patterns/docs/12-adapter.md) + lab contract test over two fake vendors. | `@ConditionalOnProperty`, `JavaMailSender`, `RestClient`, `MockRestServiceServer`, Testcontainers `GenericContainer` | — | **Adapter** | 2 h | ✅ | 2026-10-02 |
| 4.6 | Suppression list + preferences (`Audience` repository; consent order: suppressed address beats even mandatory, opt-out only for optional categories; a bounce suppresses the address). Kernel `UnsubscribeTokens` HMAC codec shared by worker (issues) and api (verifies), `NOTIFICATION_TOKEN_SECRET` file secret; footer link + RFC 8058 `List-Unsubscribe` headers. identity publishes `EmailVerified` → `SendWelcomeEmail`. api `notification` module: `GET`/`PUT /me/notification-preferences` (422 `CATEGORY_MANDATORY`), public `POST /notifications/unsubscribe` (+ `/one-click`). Java note [11 HMAC & signed tokens](patterns/java/11-hmac-and-signed-tokens.md). | `Mac` / HMAC-SHA256, `MessageDigest.isEqual`, Base64url, `INSERT … WHERE EXISTS … ON CONFLICT`, `PublicEndpoints` | — | Repository | 1.5 h | ✅ | 2026-10-02 |
| 4.7 | Angular: `/account/notifications` (one `MatSlideToggle` per category, mandatory ones locked; optimistic update + rollback + snackbar, per-category in-flight lock) and public `/unsubscribe?token=…` (confirm-then-POST so link scanners unsubscribe nobody). `NotificationApi` in `core/api`. **Live:** signup → verify → welcome email with footer link + `List-Unsubscribe` headers → one-click opt-out → a replayed event is `SUPPRESSED`. Angular note [03 Optimistic UI](patterns/angular/03-optimistic-ui-and-server-state.md). | — | `MatSlideToggle`, `MatSnackBar`, optimistic UI, immutable signal updates, `finalize` | — | 1.5 h | ✅ | 2026-10-02 |
| 4.8 | `NotificationPipelineIT`: outbox row → real relay → handler → Mailpit; a redelivered event (handler runs twice) sends one email; a worker that died mid-send is taken over after its lease (`SENT/2`); welcome headers + opt-out → `SUPPRESSED`. Playwright `notifications.spec.ts`: signup → verification email read from Mailpit → verify → welcome email → unsubscribe link → preferences page. Worker coverage floor 94 % / 82 % (measured 97 / 88). Notification LLD §10–11 complete. | Testcontainers `GenericContainer` (Mailpit), `@DynamicPropertySource`, Playwright `expect.poll` + `request` | e2e against a real inbox | — | 1.5 h | ✅ | 2026-10-02 |

---

## Phase 5 — Catalog

**Est 20 h** · Goal: courses that can be browsed, filtered and paged at scale.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 5.1 | [`docs/lld/catalog.md`](docs/lld/catalog.md) (§1–§9: aggregate boundaries, viewer-dependent visibility, Specification leaves, typed cursor, 2-statement detail, Prototype duplicate) + [ADR-0009](docs/adr/0009-keyset-pagination-over-offset.md) (keyset over OFFSET, Spring Data `Window`) | — | — | — | 1.5 h | ✅ | 2026-10-02 |
| 5.2 | JPA model (`V6__catalog.sql`, no secondary indexes yet): `Course` aggregate root (rollups kept by `addLecture`, `publishedAt` stamped once + DB `CHECK`, micros-truncated times) → `Section` → `Lecture` (owning sides, `@OrderBy`, cascade + orphanRemoval), seeded two-level `Category`, instructor as id + name snapshot; one repository per aggregate. `CourseQueryCountIT` counts statements: course page **2** (entity graph `{category, sections}` + `@BatchSize` lectures; 11 / 13 without it), naive 4, lazy to-one in a list 1 + distinct, `MultipleBagFetchException` proven. `CatalogPersistenceIT`: round trip, constraints. Java note [12 JPA mapping & fetching](patterns/java/12-jpa-mapping-and-fetching.md) | associations, owning side, `@EntityGraph`, `@BatchSize`, bags, Hibernate statistics | — | Repository (one per aggregate) | 3 h | ✅ | 2026-10-02 |
| 5.3 | *(done before 5.2: the entities need it)* `Money` moves to the **kernel** as a JPA `@Embeddable` record (compile-only, optional `jakarta.persistence-api`; the compact constructor also guards rows Hibernate loads); `LectureDuration` is one column, so an auto-applied `AttributeConverter` in `infrastructure/` (the entity never names it). Kernel catalog check now covers kernel classes. Catalog row 18 ✅ | `@Embeddable` records, `AttributeConverter` + `autoApply`, optional Maven deps | — | **Value Object**: catalog row 18 | 1 h | ✅ | 2026-10-02 |
| 5.4 | Composable search filters: `CourseSpecifications` (9 leaves over the Criteria API: embedded-path `price.amountMinor`, FK id without a join, escaped case-insensitive `LIKE`), `CourseSearch` record composes the facets present with `allOf`; visibility as a sealed `Viewer` (`Anonymous \| Member \| Admin`) with SQL `visibleTo` + in-memory `canSee`. `CourseSpecificationsIT` runs every leaf on Postgres and proves the two visibility forms agree for 5 viewers. [Pattern note 08](patterns/docs/08-specification.md) + lab (full Evans form: `isSatisfiedBy` + `toWhere`, identities, nesting) | JPA Criteria API, Spring Data JPA 4 `Specification` (`allOf`, `unrestricted`), sealed types + record patterns | — | **Specification**: catalog row 8 | 2.5 h | ✅ | 2026-10-02 |
| 5.5 | Keyset pagination with an opaque cursor + the read API: `GET /courses` (query string bound to a validated record), `GET /courses/{slug}` (public, optional viewer: identity's resolver learns `Optional<CurrentUser>`), `GET /categories`, `GET /instructor/courses` (`@PreAuthorize`). Spring Data `Window` + `ScrollPosition.keyset()`; our typed, sort-bound `CourseCursor`; every sort ends in the id. **Finding:** `project()` is ignored by `scroll()` (Spring Data JPA 4.1) → fetch-join Specification. `CatalogApiIT`: 55 tied rows → 20/20/15 no dupes; publish-mid-scroll doesn't shift pages; **1 statement per page**; 404-not-403 drafts. API conventions §2 + §5 ✅ | `Window`, `KeysetScrollPosition`, record query binding, built-in method validation, `HandlerMethodArgumentResolver` + `Optional` | — | — | 2 h | ✅ | 2026-10-02 |
| 5.6 | Course duplication: `Course.duplicateAsDraft` → private copy constructor + package-private `Section`/`Lecture` copy constructors (deep: new ids, children re-parented; reset: DRAFT, no history; shared: `Money`, category, **media asset ids**); `CourseDuplicationService` (owner or admin, random `-copy-xxxxxx` slug, one transaction); `POST /instructor/courses/{id}/duplicate` with `@IdempotencyKeyRequired`. **Bug found + fixed in platform:** a replayed 201 lost its `Location` header → stored since `V7__platform_idempotency_location.sql`. [Pattern note 11](patterns/docs/11-prototype.md) + lab (`clone()` shallow trap vs copy constructors, prototype manager) | copy constructors vs `clone()` (Effective Java 13), cascade persist of a new tree | — | **Prototype**: catalog row 11 | 2 h | ✅ | 2026-10-02 |
| 5.7 | Test data builder for the catalog's aggregate: `CourseBuilder.aCourse()` + nested `aLecture()` (unique default slugs, `withSection` / `withCurriculum`, builds through `Course.draft` / `addLecture` / `publish`, so every built course is legal) + `TestInstructors` (FK rows via `JdbcClient` that joins the test transaction). All 8 catalog test classes refactored onto it (the 9-positional-argument seed is gone). `CourseBuilderTest`. [Pattern note 10](patterns/docs/10-builder.md) + lab (Bloch builder: required entry point, cross-field `build()` validation, defensive copies, `toBuilder`) | fluent APIs, nested static builders, varargs, static imports | — | **Builder**: catalog row 10 | 1.5 h | ✅ | 2026-10-02 |
| 5.8 | Angular `/courses`: every filter (search debounced, category tree, level chips, price, sort) lives in the URL, bound to inputs; infinite scroll in a `CdkVirtualScrollViewport` through one pipeline (`switchMap` per query cancels stale pages, `exhaustMap` per page, `scan`) + an a11y "Load more". `/courses/:slug`: `rxResource` by slug, 404 → "not found", curriculum `@defer (on viewport)` (own 23.5 kB chunk). `CatalogApi`, `money` + `duration` pipes, nav item. `docs/db/seed-catalog.sql` + `make seed` (deterministic; CI seeds before e2e). Playwright `catalog.spec.ts`: reload/Back keep filters, scrolling sends `cursor=`, `@defer` loads, friendly 404. Fixed `make api`/`make worker` (double `cd`). [Angular note 04](patterns/angular/04-url-state-infinite-scroll-defer.md) | — | router query params, `withComponentInputBinding`, `@defer`, `CdkVirtualScrollViewport`, `rxResource`, `exhaustMap` | — | 5 h | ✅ | 2026-10-02 |
| 5.9 | [`docs/db/indexes.md`](docs/db/indexes.md): every list query (Hibernate's real SQL) `EXPLAIN ANALYZE`d on 10,000 seeded courses, median of 7 (`docs/db/measure_catalog.py`), before/after `V8__catalog_indexes.sql`: 4 partial indexes + trigram GIN, default list **8.5 → 0.14 ms**, no Sort node left. **Findings → code:** Spring Data's keyset OR chain can't seek (2.35 ms at page 250) → `keysetBound` redundant predicate → **0.155 ms** (= row comparison); expression index needs `ANALYZE` (1.38 → 0.68 ms) → in V8. Rejected with numbers: category index (never chosen), non-partial composite (584 vs 376 kB). `CatalogIndexesIT` pins plan shapes. LLD §9–§11 + ADR-0009 outcome | `EXPLAIN (ANALYZE, BUFFERS)`, partial + expression + GIN indexes, `pg_trgm`, planner statistics | — | — | 1.5 h | ✅ | 2026-10-02 |

---

## Phase 6 — Catalog authoring

**Est 22 h** · Goal: instructors build courses through a wizard, with undo and safe
concurrent edits.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 6.1 | [`docs/lld/catalog-authoring.md`](docs/lld/catalog-authoring.md) (§1–§9: state diagram + who may move, coded publish gate, commands with stored inverses, "the root's version covers the aggregate", deferrable position constraints instead of NestJS's park-negative trick) + [ADR-0010](docs/adr/0010-optimistic-concurrency-with-version.md) (`@Version` + `expectedVersion`) | — | — | — | 1.5 h | ✅ | 2026-10-03 |
| 6.2 | Course lifecycle: sealed `CourseState` (records; default methods throw `ILLEGAL_TRANSITION`, each state overrides only its legal events), `Course.transition` (gate on submit/publish → 422 `COURSE_NOT_READY` with codes, `publishedAt` once, version bump); `CourseLifecycleService` (thin: who + load), `CourseAccess` (owner or admin, else 404); `POST /instructor/courses/{id}/{submit\|withdraw\|unpublish\|archive}`, `GET …/readiness`, `POST /admin/courses/{id}/publish` (ADMIN). The Phase 5 `publish()` placeholder is gone: the builder now goes through the real transitions. `CourseStateTest` (all 20 pairs), `CourseLifecycleIT` (incl. stale copy → `@Version`). [Pattern note 02](patterns/docs/02-state.md) + lab (3 styles compared) | `sealed interface CourseState`, default methods, exhaustive `switch`, `@Version` on transitions | — | **State**: catalog row 2 | 3 h | ✅ | 2026-10-03 |
| 6.3 | *(done before 6.2: the state machine's gated edges need it)* Publish gate: `PublishGate` = AND of 6 coded `PublishRequirement`s (description, price decided, sections, no empty section, ≥ 3 lectures, a preview) that **collects** failures: `evaluate` → checklist, `problems` → 422. `course.price_set_at` (`V10`) tells free from undecided; `Course.touch()` makes the root's version cover the aggregate. `PublishGateTest`: every code must have a failing example. Pattern note 08 §3b | composition of predicates, `Predicate<T>`, parameterised tests | — | **Specification** (explainable) | 2 h | ✅ | 2026-10-03 |
| 6.4 | Curriculum edits as commands: sealed `CurriculumCommand` (10 records, JSON `kind`), `applyTo` returns the inverse (removals carry a `SectionSnapshot`/`LectureSnapshot` Memento), ids generated before applying; `Course.apply` (editable + rollups recomputed + touch); one route `POST …/curriculum`; `V11` deferrable position constraints. **3 real-DB findings, fixed:** `orphanRemoval` deleted a moved lecture (→ explicit removal via a Spring Data custom fragment `CurriculumCleanup`), concurrent edits deadlocked (→ `lockById` first, ADR-0010 addendum), Jackson 3 rejects null primitives (→ boxed optionals). `CurriculumCommandTest` (every kind round-trips), `CurriculumCommandJsonTest`, `CurriculumIT`. API conventions §6 ✅. [Pattern note 04](patterns/docs/04-command.md) + lab | sealed records, Jackson `@JsonTypeInfo` polymorphism, deferrable constraints, `@Lock(PESSIMISTIC_WRITE)`, repository fragments | — | **Command**: catalog row 4 | 4 h | ✅ | 2026-10-03 |
| 6.5 | Undo/redo in the `course_edit` history table (`V12`, jsonb command + inverse, `undone_at` for the redo branch; `CourseEditLog` caretaker over JDBC), `POST …/curriculum/undo` · `/redo` (versioned: a double-tapped undo undoes once), `GET …/curriculum` (`canUndo`/`canRedo`). **Built both, kept one** ([ADR-0011](docs/adr/0011-undo-with-stored-inverses.md)): `UndoStrategyComparisonTest` measured 193–880 B per edit (inverse commands) vs 7,541 B (whole-curriculum snapshot) on a 10×5 course → inverses shipped, Memento only for what a removal destroys. `UndoRedoIT`. [Pattern note 05](patterns/docs/05-memento.md) + lab (snapshot-stack caretaker) | deep copies, `Deque`, jsonb via `JdbcClient`, Jackson polymorphism round trip | — | **Memento**: catalog row 5 | 2.5 h | ✅ | 2026-10-03 |
| 6.6 | *(done before 6.4: every write needs it)* The wizard's versioned content writes: `POST /instructor/courses` (`Idempotency-Key`; instructor name via identity's new public `IdentityApi`), `GET …/{id}`, `PUT …/{id}/details`, `PUT …/{id}/pricing` (all with `expectedVersion`). `CourseAccess.forEditing` pre-check → 409 `{expectedVersion, currentVersion}`; `@Version` catches the same-instant race; `Course.touch` makes the root's version cover the aggregate; `Slugs`. `CourseAuthoringIT`: **10 concurrent saves → exactly 1 winner**. API conventions §3 ✅. Java note [13 optimistic locking](patterns/java/13-optimistic-locking-and-lost-updates.md) | JPA `@Version`, `OptimisticLockException`, `ObjectOptimisticLockingFailureException`, latch-driven concurrency tests | — | — | 1.5 h | ✅ | 2026-10-03 |
| 6.7 | Angular instructor area: `/instructor` (my courses, `rxResource`), `/instructor/courses/new` (one Idempotency-Key per form), `/instructor/courses/:id/edit` wizard (`MatStepper`: Details autosaved · Pricing · Curriculum editor · Review checklist + submit/withdraw) behind `roleGuard('INSTRUCTOR','ADMIN')`, "Teach" nav item. Typed `FormGroup<T>`; autosave = `debounceTime` + **`concatMap`** (deviation: `switchMap` cancels only the response, the server still applies the write → self-inflicted 409s); form patched on (re)load only with `emitEvent: false`. Playwright `authoring.spec.ts`: the whole flow + **two real tabs → conflict dialog, nothing overwritten**. Angular note [05](patterns/angular/05-wizard-autosave-drag-drop.md) | — | `MatStepper`, `FormGroup<T>`, `debounceTime` + `concatMap` | — | 4 h | ✅ | 2026-10-03 |
| 6.8 | *(committed before 6.7: the wizard embeds it)* Angular curriculum editor: every change a command (`AuthoringApi` mirrors the sealed union), CDK `cdkDropListGroup` drag-drop of lectures across sections (optimistic, immutable, then `MOVE_LECTURE`), sections reordered by ↑/↓ (a11y) sending the whole order, add/rename/preview/remove; server-side undo/redo buttons from `canUndo`/`canRedo` + Ctrl/⌘+Z, Ctrl+Shift+Z/Ctrl+Y (ignored inside text fields); `CourseEditorStore` (`@Service({autoProvided: false})`, one shared version) + `ConflictDialog` on 409 → reload, never retry | — | CDK `DragDrop`, `MatDialog`, keyboard shortcuts, component-scoped signal store | — | 3.5 h | ✅ | 2026-10-03 |

---

## Phase 7 — Media + transcode pipeline

**Est 30 h** · Goal: upload → playable HLS in under 5 minutes, surviving crashes without
duplicates.

> ✅ **Decided in 7.1 ([ADR-0012](docs/adr/0012-seaweedfs-as-local-s3.md)):** MinIO's images stopped
> being published (2026-10-02). SeaweedFS replaces it, after a probe ([`docs/media/s3_probe.py`](docs/media/s3_probe.py))
> of SeaweedFS, RustFS, Garage and LocalStack for presigned multipart, `ListParts`, and CORS that
> exposes `ETag` and refuses foreign origins.

| # | Task | Java / Spring concept | Angular concept | Pattern & force | Est | Status | Date |
|---|---|---|---|---|---|---|---|
| 7.1 | [`docs/lld/media.md`](docs/lld/media.md) (§1–§9: upload-session state table incl. replays, `PartPlan` ≤ 100 parts ≥ 5 MiB, deterministic `MediaKeys` in a shared `backend/storage` module, catalog asks `MediaApi.readiness` at the gate instead of keeping a copy, playback ticket because native Safari HLS can't send a bearer) + [`video-pipeline.md`](docs/lld/video-pipeline.md) (§1–§9: `pipeline_job` DAG PROBE → TRANSCODE×rung + THUMBNAILS → PACKAGE, outbox-style lease, commit markers written last, conditional asset updates) + [ADR-0012](docs/adr/0012-seaweedfs-as-local-s3.md) local S3 (**4 servers probed**: LocalStack needs a licence, Garage allows foreign-origin preflights with `*`, RustFS too young → **SeaweedFS**; compose `seaweedfs` + `media-init` via aws-cli, verified end to end) + [ADR-0013](docs/adr/0013-hls-for-playback.md) HLS/fMP4 + [ADR-0014](docs/adr/0014-upload-progress-from-the-provider.md) provider truth (server completes from `ListParts`; the client never sends ETags) | — | — | — | 2.5 h | ✅ | 2026-10-13 |
| 7.2 | New library module `backend/storage` (auto-configured like `messaging`): `ObjectStorage` adapts the AWS SDK v2 (create/presign/list/complete/abort multipart, `listMultipartUploads`, head, put, presigned GETs) with **no interface** (one vendor API, a real container in tests); SDK failures → `StorageUnavailableException` / `NoSuchUploadException` / `StorageException`; **two presigners** (SigV4 signs the host: browser urls use `public-endpoint`, worker urls the internal one); SDK 2.30+ default checksums switched to `WHEN_REQUIRED` (else presigned urls demand headers browsers never send); `MediaKeys` key layout. `ObjectStorageTest` (translation over a stubbed `S3Client`), `ObjectStorageIT` (SeaweedFS Testcontainer, shared as a test-jar: resume from `ListParts`, tampered url → 403, idempotent abort, overwrite, ranged GET 206). 98 % lines. Pattern note 12 §1b + lab `adapter/storage` | AWS SDK v2, `S3Presigner`, test-jars, auto-configuration | — | **Adapter**: catalog row 12 | 2.5 h | ✅ | 2026-10-13 |
| 7.3 | Media module (`V13`: `media_asset`, `upload_session` with the plan invariants as CHECKs, `media_rendition`): `PartPlan` (≤ 100 whole-MiB parts ≥ 5 MiB; `check` vs the provider: missing / wrong size / unexpected); sealed `UploadState` answering **PERFORM / REPLAY / 409** per event (rejection code names the *current* state), `UploadSession.state(now)` = an effective `Expired` before the sweeper runs; `UploadService` create (provider first, then rows) · resume (`ListParts` → fresh urls for what's missing) · complete (row lock → `ListParts` → plan check → complete with the **provider's** ETags → `HEAD` → `MediaUploaded` in the outbox) · abort; `NoSuchUpload` on complete → `HEAD` heals "S3 completed, our commit didn't"; `StorageExceptionHandler` → 503 + `Retry-After`; `MediaApi` (readiness, ownership) for catalog. **Built differently:** 409 not 410, 422 not 415 (sealed error kinds). SeaweedFS joins `make up` and the shared `TestcontainersConfiguration`. `PartPlanTest`, `UploadStateTest` (16 cells), `UploadIT` (resume after parts 1+3 → exactly part 2; **10 concurrent completes → 1 event**; heal path; abort ×2; 404 for others; 422s; learner 403). Conventions §7 (bounded sizes stay numbers) + §8 ✅. Pattern note 02 §5b + lab `UploadLifecycle` | `@Lock(PESSIMISTIC_WRITE)`, `DynamicPropertyRegistrar`, `@RestControllerAdvice` ordering, `Math.ceilDiv` | — | **State**: catalog row 2 | 3.5 h | ✅ | 2026-10-13 |
| 7.4 | `V14`: `pipeline_job` (unique `(asset, type, rung)` = idempotent enqueue; partial due-index; `next_attempt_at` doubles as the lease, like the outbox) + `media_asset.has_audio`. `MediaUploadedHandler` only **enqueues** PROBE; the DAG unfolds as jobs finish: PROBE → `TRANSCODE` × the ladder's rungs + `THUMBNAILS` → `PACKAGE` (fan-in by whichever rung finishes last, under the asset's row lock) → `READY` (`Readiness`: whichever of PACKAGE / THUMBNAILS is second). `JobProcessor<R>` **Template Method** (final `run`: absolute work dir, hook `process`, commit markers uploaded **last**, a deferred `Completion` with `record`/`next` hooks, cleanup in `finally`); `JobProcessorRegistry` (every `JobType` exactly once, checked at boot); `JobRunner` (5 s heartbeat with progress, no transaction while ffmpeg runs, success = lock asset + DONE-if-still-leased + effects + successors in one transaction; permanent → DEAD + asset FAILED at once, temporary → backoff 30 s × 4ⁿ ± 20 %); `JobPoller` (claims only free slots, virtual threads); `PipelineStore` (conditional status updates, rendition upsert); `WorkDirCleanup`. **Bug found:** a relative work dir resolved twice (ffmpeg runs in it) → made absolute in the template. `JobProcessorTest` (markers last whatever the hook declares; cleanup after a throw), `JobProcessorRegistryTest`, `PlaylistsTest` (master, rung refs, sprite VTT), `JobRepositoryIT` (6 jobs × 4 concurrent claimers → no duplicates; a lapsed lease → reclaim, the stale finisher changes nothing), `JobRunnerTest` (success, stale lease, backoff 2 m ± 20 %, permanent vs last attempt, heartbeats with progress), `JobPollerTest` (a busy slot claims nothing), `PipelineIT` (real upload → **READY** with 5 jobs, 2 renditions, master + segments + poster + VTT; `MediaUploaded` ×2 → one pipeline; junk → DEAD in 1 attempt, `MEDIA_UNREADABLE`). Worker coverage 96 % lines / 87 % branches (floors raised to 95 / 85). Pattern notes 06 §1b + 09, lab `template/Job` | abstract generic base classes, `EnumMap`, `TransactionTemplate`, `FOR UPDATE SKIP LOCKED`, `Semaphore` | — | **Template Method** + **Factory Method**: catalog rows 6, 9 | 5 h | ✅ | 2026-10-13 |
| 7.5 | *(done before 7.4: the job processors run it)* `FfmpegCommand` builds one HLS rendition's ~40 ordered arguments as a `List<String>` (never a shell string: a presigned url's `&`/`?` stay one argument), GOP/segment rule in `build()`, time-based closed GOPs aligned across rungs; `StillCommands` (poster, sprite: static factories, nothing to configure); `LadderProfile` → `StandardLadder` / `FastLadder` by property, never-upscale + keep-one-rung as a shared default method; `ProcessRunner` (stderr to a **file**, so a flood can't deadlock the pipe; stdout read on a virtual thread; timeout → `destroyForcibly`; input errors classified **permanent**); `Ffprobe` (JSON, cover art ≠ video); `ProgressParser` (`out_time_us`, 100 only at `progress=end`). ffmpeg in the worker image only and on CI runners. `FfmpegCommandTest`, `LadderProfileTest`, `ProcessRunnerTest` (real `sh` children: 2 MB stderr, hang → killed in < 5 s), `FfprobeTest`, `FfmpegIT` (real ffmpeg on a generated `testsrc2` clip: probe → HLS files exactly as `MediaKeys` names them, **rerun = identical file set**, poster + sprite, junk → permanent). Pattern notes 01 §7b + 10 §3b, labs `strategy/ladder` + `builder/TranscodeArgs` | `ProcessBuilder` redirects, `Process.waitFor(timeout)`, virtual threads, Jackson 3 `JsonNode` | — | **Builder** + **Strategy**: catalog rows 10, 1 | 3 h | ✅ | 2026-10-13 |
| 7.6 | **Retries are rows** (7.4's `next_attempt_at` backoff survives a crash and frees the slot; no Spring Retry / Resilience4j: in-process retry state dies with the process). Dead letters: `GET /api/v1/admin/media/jobs/dead` + `POST …/{id}/replay` (ADMIN; `DeadJobs` domain interface + `JdbcDeadJobs`; DEAD → PENDING with a fresh budget, asset FAILED → UPLOADED for a PROBE, else PROCESSING; 2nd replay → 409 `JOB_NOT_DEAD`). `UploadSweeper` in the worker (`@Scheduled` every 15 min + **ShedLock** `V15`, `usingDbTime`): OPEN sessions past expiry → abort at the provider **first** (the bill stops), then EXPIRED + asset FAILED (`UPLOAD_EXPIRED`), conditional; provider uploads with no open session and > 24 h old → abort. `DeadJobReplayIT`, `UploadSweeperIT` (moved clock: expired vs live, orphan only after a day, idempotent twice, **a 2nd replica inside `lockAtLeastFor` skips the sweep**) | `@Scheduled(cron)`, ShedLock `@SchedulerLock` + `JdbcTemplateLockProvider`, DB time vs host time | — | — | 2.5 h | ✅ | 2026-10-13 |
| 7.7 | Worker `ProgressPublisher` (Redis pub/sub `media:asset:{id}`: `progress` ticks ≤ 1/s per job, lossy; `changed` after every outcome; never fails a job) wired into `JobRunner`. Api: `GET /media/assets/{id}` (snapshot: status + per-job progress, mean held < 100 until READY) and `GET …/stream` (`SseEmitter`: snapshot → ticks → fresh DB snapshot on `changed` → closed at READY/FAILED; register-then-snapshot = duplicates never gaps; keep-alive comment every 20 s; `no-cache` + `X-Accel-Buffering: no`); `AssetEvents` hub (one `RedisMessageListenerContainer` per replica, **started in the background with retry**: the api must boot without Redis); access checked once at open, refreshes run unchecked on the listener thread. `DeadJobs` → `PipelineJobs` (+ `progressOf`). **Found:** Spring Data Redis registered a second `redisMessageListenerContainer` for Redis *repositories* (unused) → `spring.data.redis.repositories.enabled: false`. **Deviation:** the SPA reads the stream with `HttpClient` + `reportProgress` (bearer kept), not `EventSource` (no auth header). `ProgressPublisherTest` (throttle, Redis down), `JobRunnerTest` (+ ticks and `changed`), `AssetProgressIT` (**real server + real JWT**: snapshot → tick → READY closes the stream, headers; a finished asset gets one snapshot; someone else's → 404; polling GET). API conventions §9 ✅ | `SseEmitter`, async requests, `RedisMessageListenerContainer`, `PatternTopic`, Redis pub/sub vs streams | — | — | 2.5 h | ✅ | 2026-10-13 |
| 7.8 | **Catalog ↔ media** (backend, needed for the uploader to mean anything): `ATTACH_MEDIA` command (11th; inverse re-attaches the previous asset → undoable; VIDEO only), `CurriculumService` checks `MediaApi.isOwnedBy` (course's instructor; else 400 `UNKNOWN_ASSET`); `PublishGate.evaluate(course, readyAssets)` adds **`MEDIA_NOT_READY`** while staying pure, `CourseLifecycleService` asks `MediaApi.readiness` on gated edges and `Course.adoptMediaDurations` takes the probed lengths; authoring `CurriculumResponse` carries `assetId` (the public course page still doesn't). `CourseBuilder` gives VIDEO lectures an asset by default (+ `withoutVideo()`), `TestMedia` makes ITs' videos READY, `TestInstructors.deleteAll` clears media first. `LectureMediaIT`. **Angular:** `MediaApi` (+ pure `parseSse`; SSE read through `HttpClient` `partialText`, not `EventSource`), `ChunkedUploader` (`mergeMap(…, 4)` over `Blob.slice`s, per-part `retry` around `defer` with backoff, resume by file fingerprint → `GET` asks storage, never a bearer to S3), `LectureVideo` widget in the curriculum editor (upload bar → ATTACH_MEDIA → live processing → ready/failed in words; follows `assetId` with `effect` + `untracked`). Specs for all four (145 frontend tests). e2e authoring lectures are now articles. New code is all in the lazy chunk (the 666 kB initial-bundle warning predates it). [Angular note 06](patterns/angular/06-chunked-upload-and-sse.md) | `Set`/`Map` inputs to a pure Specification, record patterns in `instanceof` | `mergeMap` concurrency, `retry` + `defer`, `HttpClient` streaming `partialText`, `effect` + `untracked`, `Blob.slice` | — | 6 h | ✅ | 2026-10-13 |
| 7.9 | **Playback** (backend, ADR-0013): `POST /media/assets/{id}/playback` (bearer; owner/admin, READY else 409 `MEDIA_NOT_READY`) issues a 6 h `PlaybackTickets` HMAC ticket bound to one asset; public `GET …/hls/master.m3u8?t=` (rung URIs get `?t=`) and `…/hls/{rung}/index.m3u8?t=` (`EXT-X-MAP` + segments → presigned storage urls, via pure `Playlists.rewrite`); `PLAYBACK_TOKEN_SECRET` as a file secret (`make secrets`). **Angular:** `shared/hls-player` (hls.js 1.7.3 via dynamic `import()` in its own 150 kB-gz chunk, initial bundle unchanged; `afterNextRender`; native HLS fallback; destroy on destroy; fatal errors only) + Preview in `LectureVideo`. **e2e `media.spec.ts`** (real stack): upload a 54 KB fixture straight to storage → real ffmpeg in the worker → SSE to "Video ready" → preview plays (`currentTime > 0.5`). **Found by the stack + e2e (4 bugs, each fixed):** the worker's `/tmp` is a 64 MB tmpfs and ffmpeg shares its 1 GB limit → a `pipelinework` volume (dir created `app`-owned in the image), 2 GB, heap 40 %; **Alpine's ffmpeg 8.1** (unlike Homebrew's 9.0) refuses a JPEG from limited-range YUV → explicit `out_range=full,format=yuvj420p`; and emits **no** sprite for a clip shorter than the interval with `fps=1/10` → `select='isnan(prev_selected_t)+gte(…)'` + `-fps_mode vfr`; a tool that "succeeds" without writing its file now fails clearly in the template (`JobProcessorTest`, `FfmpegIT` short-clip test). Conventions §11 ✅ (6 h, not 5 min: ABR fetches other rungs' playlists late). `PlaybackTicketsTest`, `PlaylistsTest`, `PlaybackIT` (a presigned segment fetched without a bearer), `hls-player.spec` (`vi.mock('hls.js')`), `lecture-video.spec` (+ preview). Angular note 06 §7b | HMAC tickets, constant-time compare, `CacheControl.cachePrivate` | `afterNextRender`, `viewChild`, dynamic `import()`, `vi.mock` | — | 4.5 h | ✅ | 2026-10-13 |
| 7.10 | **`PipelineCrashIT`**: the real repackaged worker jar as a **child JVM** (test Postgres + SeaweedFS + Redis, fast ladder, 4 s lease); a control asset runs clean; the same 120 s clip again is **SIGKILLed** (JVM + ffmpeg children) the moment a rung has segments but no `index.m3u8`, its half-state is asserted (no rung playlist, no master), a second worker finishes → object keys **identical** to the control's, one rendition per rung with equal segment counts, every job DONE, only the killed job at 2 attempts. 3/3 green (~35 s). Both LLDs §10–§11 finished | `ProcessHandle.current().info().command()`, `Process.descendants()`, `destroyForcibly` (SIGKILL), `JdbcConnectionDetails` / `DataRedisConnectionDetails` | — | — | 2 h | ✅ | 2026-10-13 |

---

## Phase D3 — Observability

**Est 12 h** · Goal: see what the system is doing. Metrics, traces and logs, correlated,
with alerts that mean something.

| # | Task | DevOps concept | Est | Status | Date |
|---|---|---|---|---|---|
| D3.1 | Micrometer → Prometheus: `micrometer-registry-prometheus` in both apps, `/actuator/prometheus` (public on the app port; nginx only forwards `/api/`), `application` tag on every series. HTTP latency as **9 chosen SLO buckets** incl. 300 ms (the full histogram measured ~54 buckets per uri × method × status series). Custom: `outbox.events.written{event.type}` (business facts, **counted after commit**; orders arrive with Phase 9's events, no new code), `outbox.messages{event.type,outcome}` + `outbox.delivery.lag` (commit → handled, 60 s SLO edge) in `OutboxRelay`, `OutboxBacklogMetrics` gauges (backlog + oldest age: a *stopped* relay still shows), `pipeline.job` **Observation** (timer by `job.type` × `outcome`; ids as high-cardinality → spans only) = the transcode duration, `JobPoller` as a `MeterBinder` (slots busy/total, queued). `TransactionalOutboxIT` (+3: rollback not counted, outcomes + 10 s lag, gauges see a backlog with no relay), `JobRunnerTest` (outcome tags), `JobPollerTest` (saturation), `JobRepositoryIT` (`countDue`), `PrometheusScrapeIT` + `WorkerApplicationIT` (`@AutoConfigureMetrics`: the real scrape). [DevOps note 05](patterns/devops/05-observability.md) §1–§2 | RED/USE metrics, cardinality | 2.5 h | ✅ | 2026-10-13 |
| D3.2 | Compose profile **`observability`** (`make obs`): Prometheus v3.15.0 (:9091, 7 d retention, `--web.enable-lifecycle`) + Grafana 13.2.3 (:3001, anonymous **Viewer** + `viewers_can_edit` for Explore: Grafana 13 refuses other anonymous roles; **no state volume**). `deploy/observability/`: `prometheus.yml`, datasource with a fixed `uid`, a file provider (`allowUiUpdates: false`) and **4 dashboards** — HTTP (RED: 5xx ratio, share under 300 ms, quantiles from buckets, slowest routes; `/actuator` + SSE streams excluded), JVM (heap vs max, live data after GC, GC pause share, threads, CPU), Hikari (USE: in use / max, threads waiting, acquire + hold time, timeouts), Pipeline & outbox. `make obs-check` (promtool). **Found:** listing each app as container *and* host target scraped the container twice (the host port is its published port) → every `sum()` doubled; now one target via `host.docker.internal`, which reaches `make stack` and `make api` alike. Every panel query run against the live stack with traffic. Note 05 §3 | dashboards-as-code | 2.5 h | ✅ | 2026-10-13 |
| D3.3 | `spring-boot-starter-opentelemetry` in both apps (Micrometer Tracing → OTel SDK → **OTLP/HTTP → Tempo 2.10.8**, its OTLP-metrics registry excluded). **Through the outbox** (ADR-0015): `V16` `outbox_message.headers JSONB`; `JdbcOutboxWriter.append` = PRODUCER observation (`SenderContext` → Boot injects `traceparent`), `OutboxRelay` delivers in a CONSUMER observation (`ReceiverContext` from the row) → the worker's span is the api request's grandchild. `OutboxMessage` gains `headers` (old constructor kept). `@Scheduled` observations off (a root trace per poll otherwise). Sampling 1.0 locally via `TRACING_SAMPLING_PROBABILITY`; the endpoint is an **env var only** (the property's presence turns the exporter on). **Verified live:** one signup = one Tempo trace, 8 spans, api + worker. `OutboxTracePropagationTest` (real OTel SDK + in-memory exporter, no Spring/DB: one trace id, CONSUMER's parent = PRODUCER; error status; tracing off → empty headers, fresh trace), `TransactionalOutboxIT` (headers round-trip, NULL → empty), **`TraceCorrelationIT`** (ECS JSON log line of the request has the trace id that's in the outbox row's `traceparent`; **found**: Boot's ECS writer emits top-level `traceId`, not ECS `trace.id`). Tempo is distroless → no compose healthcheck. ADR-0015 (rejected: OTel Java agent, id columns, span links). Note 05 §4 | distributed tracing, context propagation | 3 h | ✅ | 2026-10-13 |
| D3.4 | **Loki 3.7.8** (single binary, tsdb v13, 72 h retention, structured metadata on) + **Grafana Alloy v1.20.1** (`discovery.docker` filtered to this compose project → `loki.process`: ECS JSON → `level` **label**, `traceId`/`spanId` **structured metadata**, never labels). Grafana: Loki `derivedFields` (log → *Open trace*) and Tempo `tracesToLogsV2` (span → its logs across services, `{service=…} \| traceId=…`). **Found:** the worker logged nothing on a successful email send → `NotificationService` logs template, event id and provider id (no address). **Verified live:** signup → worker line in Loki with `traceId` as metadata → the same id opens the api + worker trace in Tempo, and the trace's LogQL returns the line; all 3 datasources healthy after provisioning. Loki distroless, Alloy without `wget` → no healthchecks. Note 05 §5 | centralized logging | 1.5 h | ✅ | 2026-10-13 |
| D3.5 | **3 SLOs** (30 d): api availability 99.5 % (no 5xx), api latency 95 % < 300 ms (the exact `le="0.3"` edge), outbox freshness 99 % < 60 s; recording rules over 5 m / 30 m / 1 h / 6 h; **multi-window burn-rate** alerts — FastBurn 14.4× (1 h AND 5 m) → page, SlowBurn 6× (6 h AND 30 m) → ticket; symptom alerts where the SLI goes NaN: `ServiceDown` (`max by (job)(up)`), `OutboxStalled` (oldest age), `DeadLetters` (outbox or pipeline). **Alertmanager v0.34.1 → Mailpit** (page/ticket routes; inhibition: a down app mutes its SLO echoes, a page mutes its ticket). **`promtool test rules`** (9 cases incl. 4 % → ticket not page, the page *resolves* while the 1 h ratio still remembers, idle → nothing) + `amtool check-config` + **every dashboard query parsed by promtool** (`scripts/dashboards_as_rules.py`; a broken query fails it, checked) = `make obs-check` = new CI job `observability`. SLO dashboard (budget left, burn rates, firing alerts). **6 runbooks** in `docs/runbooks/` linked from every alert. **Verified live:** stopped the worker → `[PAGE] ServiceDown FIRING` in Mailpit after 2.5 min with the runbook URL → restarted → RESOLVED ~4.5 min later (`group_interval`). Note 05 §6–§9; HLD §4 | SLO/SLI, error budgets | 2.5 h | ✅ | 2026-10-13 |

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
- [x] generics (bounded, wildcards) · [x] Streams + Collectors · [x] `Optional` · [x] exception hierarchies
- [x] `equals`/`hashCode`/immutability · [x] interfaces vs abstract classes · [x] package-private encapsulation
- [x] threads, executors, `CompletableFuture` · [x] virtual threads (config) · [x] locks / atomics · [x] `ProcessBuilder`

### 3.2 Spring

- [x] Boot auto-configuration + starters · [x] `@RestController` / records as DTOs · [x] ProblemDetail · [x] Actuator
- [x] DI deep-dive (scopes, profiles, `@ConfigurationProperties`) · [x] AOP + proxies · [x] Bean Validation
- [x] Spring Data JPA / Hibernate · [x] Flyway · [x] transactions + propagation · [x] Spring Security 7 + JWT
- [x] Spring Modulith events · [x] Modulith verification · [ ] Spring Cache + Redis · [x] `@Scheduled` / ShedLock · [x] SSE
- [x] Testcontainers + `@ServiceConnection` · [x] `MockMvcTester` · [x] `@WebMvcTest` / `@DataJpaTest` slices

### 3.3 Angular

- [x] standalone components · [x] `@Service()` + `inject()` · [x] `toSignal` · [x] `@switch` / `@let` control flow · [x] lazy routes
- [x] `signal` / `computed` / `effect` · [x] `input()` / `output()` · [x] typed reactive forms · [x] interceptors · [x] guards
- [x] RxJS operators in anger · [x] Material (stepper, dialog; table later) · [x] CDK drag-drop · [x] CDK virtual scroll · [x] `@defer`
- [x] Vitest + `HttpTestingController` · [x] Playwright e2e

### 3.4 DevOps

- [x] multi-stage Dockerfile · [x] layered jars · [x] non-root + healthcheck · [x] compose profiles + healthchecks
- [x] GitHub Actions (path filters, caching, matrix) · [x] Trivy · [x] Dependabot · [ ] GHCR · [ ] SBOM + cosign · [ ] release automation
- [x] Prometheus · [x] Grafana · [x] OpenTelemetry · [x] Loki · [x] SLO alerts
- [ ] kubectl · [ ] Deployments / Services / Ingress · [ ] probes + limits · [ ] Helm · [ ] operators · [ ] HPA / KEDA · [ ] Argo CD
- [ ] Terraform · [ ] OIDC to AWS · [ ] ECS / RDS / ElastiCache / CloudFront

---

## 4. Interview-evidence checklist

Each line is a sentence you can say *and* a file or test you can show.

- [ ] "Module boundaries are enforced by a test." → `ModularityTests` + generated module diagram
- [ ] "Every pattern is cataloged and verified." → `patterns/README.md` + `PatternCatalogIntegrityTest`
- [ ] "Webhooks are exactly-once in effect." → the 50× concurrent webhook IT (9.8)
- [x] "Transcoding survives a crash without duplicates." → the SIGKILL IT (7.10): `PipelineCrashIT`
- [ ] "Access decisions are explainable." → the policy chain with reason codes (8.2) + parameterized matrix test
- [ ] "I can show p95 under load." → k6 report (D5.1)
- [ ] "Deploys are git commits." → Argo CD sync history (D4.7)
- [ ] "Images are scanned, signed and have SBOMs." → CI run (D2.3)
- [x] "I can trace one request across api → outbox → worker." → `OutboxTracePropagationTest` + `TraceCorrelationIT`; live: one signup = one Tempo trace across both services (D3.3)

---

## 5. Deviations from the plan

| Deviation | Why |
|---|---|
| D3.1 has no `orders` metric | there are no orders until Phase 9. `outbox.events.written{event.type}` counts every committed business event, so order events show up there with no new code |
| D3 observability runs as a compose profile (`make obs`), not on Kubernetes | Kubernetes arrives in D4; the configs in `deploy/observability/` carry over (fixed datasource uids, file provisioning) |
| Tempo, Loki and Alloy have no compose healthchecks | their images are distroless or lack `wget`; Grafana waits for `service_started` and its datasources retry |
| One `backend/Dockerfile` with `--build-arg APP=api\|worker` instead of one Dockerfile per app | Both apps build identically; one file can't drift from the other |
| `spring-modulith-starter-jpa` removed from the Initializr selection | Its event-publication table would need a hand-written migration now. Phase 2 hand-rolls the outbox first, then compares (2.5). |
| No generic `processed_event` table in Phase 4 | email is an external effect; it needs the per-email claim + state machine. A generic table arrives with the first DB-effect consumer (Phase 9). Notification LLD §6. |
| MinIO moved to an opt-in compose profile (`media`), then replaced by SeaweedFS (7.1), which joined the default `make up` in 7.3 | its images are no longer published (2026-10-02); ADR-0012 probed 4 replacements |
| google-java-format pinned to 1.35.0 | 1.36+ pulls in a commonmark dependency that Spotless 3.10 fails to load |
| Healthcheck via BusyBox `wget` (was bash `/dev/tcp`) | Never install curl/wget just for a healthcheck (they add CVEs). Since D1.2 the runtime is the Alpine JRE ([ADR-0007](docs/adr/0007-alpine-jre-runtime-image.md)), whose BusyBox already has `wget` and has no bash |
| First CI run failed: `aquasecurity/trivy-action@0.33.1` (tags now carry a `v` prefix) | Fixed to `@v0.36.0` in 94f94b0. Lesson: pin action versions you have verified exist. |
| The outbox relay runs inside the **api** process in Phase 2 (not the worker, as the HLD shows) | The relay needs the outbox repository; sharing it with the worker needs a shared Spring library module. Phase 4 (4.2) extracts `backend/outbox` and moves the relay; until then `masternova.outbox.relay-enabled` controls it. |
| `SecurityConfig` is `@ConditionalOnWebApplication(SERVLET)` | Found in 2.4: a non-web context (`@SpringBootTest(webEnvironment = NONE)`) has no `HttpSecurity` bean. |
| Security config lives in **identity**, not platform (3.4); public routes come from each module's `PublicEndpoints` bean | identity owns authentication; platform must boot standalone (`PlatformModuleIT`) without a JWT decoder, and a central list of public URLs would couple identity to every module. |
| `ProblemTypes` moved to the platform's public API | `ModularityTests` rejected identity using it from the internal `platform.web` package. |
| App packages are `com.masternova.api.*` / `com.masternova.worker.*` (not `com.masternova.*`) | Keeps Modulith from treating the shared `kernel` package as an api module |
| Phase 6 order: 6.1 → 6.3 → 6.2 → 6.6 → 6.4 → 6.5 → 6.7 → 6.8 | the state machine's gated edges need the gate (6.3); every content write and command needs the versioned access (6.6) |
| Phase 7 runs on JDK 26 locally (Homebrew), CI stays on Temurin 25 | SDKMAN's Temurin 25 was gone from the dev machine on 2026-10-13; the build targets `release 25`, so the bytecode is identical |
| Phase 7 PR CI caught 3 things local runs couldn't | a storage test borrowed the dev machine's AWS credentials (now static keys); CodeQL `java/xss` on the playlist routes, which echo the ticket (now `produces` HLS only + url-encoded ticket); Trivy found libtiff CVE-2026-4775 in the web image because CI served `apk upgrade` from a stale cached layer (now `APK_REFRESH` = the UTC day busts it daily) |
| Phase 7 order: 7.1 → 7.2 → 7.3 → 7.5 → 7.4 → … | the job processors (7.4) run ffmpeg, so its builder, ladder and runner (7.5) came first |
| 5.3 done before 5.2 | the entities embed `Money` and `LectureDuration`, so the value objects came first |
| Phase 7 review (`code-review` on the whole branch) found 10 bugs, all fixed with a regression test: a JVM `Error` left the heartbeat holding a lease forever (interrupt in `finally`); a job that OOM-kills its worker was reclaimed forever (`buryAbandoned` at max attempts); a deploy's interruption counted as a failed attempt (`JobInterruptedException` → `release`, uncounted); startup cleanup judged a running job by its dir's own mtime (newest change in the tree); a source < 16 px or 0 high made an invalid rung after 5 encodes (permanent at probe; rung names `[0-9]{2,4}p`); the sweeper's two updates weren't atomic (one transaction); expired presigned part urls were retried blindly (403 → fresh url from the api); a 409 on complete kept the dead upload for resume (forgotten); the progress bar ignored live ticks; a server-closed stream (30 min) left the widget stuck (reconnect) | review findings |
| Phase 6 review found 5 bugs, all fixed with regression tests (each verified to fail without its fix): autosave reverted text typed during a save (`untracked`), parallel writes from one tab caused false 409s (one `concatMap` write queue of thunks in the store), the create form stuck after a 4xx (new Idempotency-Key per attempt), Ctrl+Z on other steps undid curriculum edits (shortcuts only on the curriculum step), client-chosen ids could hit another course's primary key → 500 (server-assigned ids) | review findings |
| Phase 5 review (`code-review` on the whole branch) found 2 bugs, both fixed with a regression test: the search box swallowed a repeated search after "Clear filters" (`distinctUntilChanged` → compare with the URL), and price sorts mixed currencies (→ one catalog currency, INR: `Course.CATALOG_CURRENCY` + `V9`) | review findings |
| `V7` is a platform fix (idempotency `Location`), catalog indexes are `V8` | 5.6's duplicate IT found that a replayed 201 had no `Location`; a migration can't wait for 5.9 |
