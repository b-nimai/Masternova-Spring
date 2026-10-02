# Patterns & Java — study hub

> Your study material for this project. **Java notes** teach the language, **pattern notes**
> teach the designs, and every note is paired with real, tested code you can run and change.

**Last updated:** 2026-10-02

## Where to start

| Order | Read | Why |
|---|---|---|
| 1 | [Java notes](java/README.md), in number order | The language and Spring features every pattern below is built from (01–07 Java, 08–10 Spring) |
| 2 | [Angular notes](angular/README.md) | The frontend side: signals, components, RxJS |
| 3 | Pattern notes ([catalog below](#catalog)), as each one lands in the project | The design: problem → structure → code → when *not* to use it → interview Q&A |
| 4 | The code + tests in [`lab/`](lab/) (and the learning tests in `backend/api/src/test/.../learning/`) | Run it, break it, change it: `cd patterns/lab && ./mvnw test` |

**Priority marks** in every note:

- **⭐⭐⭐ must know:** interview-critical, and the bugs that come from not knowing it.
- **⭐⭐ use daily:** what you'll write in every Spring module.
- **⭐ good to know:** depth for later.

In code, comments starting with **`// ⭐`** mark the lines worth remembering.

**Revision mode:** every note ends with **Interview Q&A** and a **30-second recall**. Reading
only those two sections across all notes is a full revision pass.

## How this folder works

| Piece | Where | What it gives you |
|---|---|---|
| **Java notes** | [`java/NN-<topic>.md`](java/README.md) | One language/Spring topic in depth, built around the code in `lab/.../java/` and the backend learning tests |
| **Angular notes** | [`angular/NN-<topic>.md`](angular/README.md) | One frontend topic in depth, built around code in `frontend/src/app/` |
| **Pattern catalog** | this file, below | One row per pattern: where it lives in the product, and its status |
| **Pattern notes** | [`docs/NN-<pattern>.md`](docs/_TEMPLATE.md) | Intent, the Masternova problem, UML, code walkthrough, when *not* to use it, interview Q&A, 30-sec recall |
| **Real class** | `backend/**` | The production class, marked `@DesignPattern(value = …, role = …)` |
| **Lab** | [`lab/`](lab/) | Runnable, Spring-free code + tests: pattern copies in `com.masternova.patterns.*`, Java topics in `com.masternova.java.*` |

The catalog is checked by tests, so it can't go stale:

- `kernel` → `PatternCatalogTest`: every Note/Lab link below points at a real file.
- `api` / `worker` → `PatternCatalogIntegrityTest`: every class in the *Real class* column exists
  and carries `@DesignPattern`.

**Find every pattern in the code:** `grep -rn "@DesignPattern" backend/*/src/main`

**Adding a pattern when it lands in real code:**

1. Annotate the class: `@DesignPattern(value = Pattern.STATE, role = "ConcreteState", note = "patterns/docs/02-state.md")`.
2. Copy `docs/_TEMPLATE.md` → `docs/NN-<pattern>.md` and fill it in.
3. Add a simplified copy + test under `lab/src/{main,test}/java/com/masternova/patterns/<pattern>/`.
4. Fill in the row below: Real class (fully qualified name in backticks), Note, Lab, and Status `✅`.

**Status:** `☐` planned · `🔨` in progress (note + lab exist, real code pending) · `✅` in real code + note + lab

## Catalog

<!-- catalog:start -->
| # | Pattern | Type | Where in Masternova | Real class | Note | Lab | Status |
|---|---------|------|---------------------|------------|------|-----|--------|
| 1 | Strategy | Behavioral | Payment providers (Phase 9) · auth methods (Phase 3) · ABR transcode ladder (Phase 7) | — | [note](docs/01-strategy.md) | [lab](lab/src/main/java/com/masternova/patterns/strategy/) | 🔨 |
| 2 | State | Behavioral | Course lifecycle (Phase 6) · order state machine (Phase 9) · upload session (Phase 7) | — | — | — | ☐ |
| 3 | Chain of Responsibility | Behavioral | Entitlement policy chain, explicit DENY wins (Phase 8) | — | — | — | ☐ |
| 4 | Command | Behavioral | Undoable curriculum edits (Phase 6) | — | — | — | ☐ |
| 5 | Memento | Behavioral | Snapshot/restore for curriculum undo (Phase 6) | — | — | — | ☐ |
| 6 | Template Method | Behavioral | Pipeline job base class · email templates (Phases 4, 7) | — | [note](java/06-oop-composition-over-inheritance.md) | [lab](lab/src/main/java/com/masternova/java/oop/template/) | 🔨 |
| 7 | Observer | Behavioral | Domain events: `EventPublisher` → `@TransactionalEventListener` observers; outbox handlers (Phases 2, 4) | `com.masternova.api.platform.events.TransactionalEventPublisher` | [note](docs/07-observer.md) | [lab](lab/src/main/java/com/masternova/patterns/observer/) | ✅ |
| 8 | Specification | Enterprise | Catalog filters (JPA `Specification`) · coupon rules · publish gate (Phases 5, 6, 9) | — | — | — | ☐ |
| 9 | Factory Method / Registry | Creational | Job processor registry · email template registry (Phases 4, 7) | — | — | — | ☐ |
| 10 | Builder | Creational | ffmpeg HLS command builder · test data builders (Phases 5, 7) | — | — | — | ☐ |
| 11 | Prototype | Creational | Course duplication (Phase 5) | — | — | — | ☐ |
| 12 | Adapter | Structural | Razorpay gateway · mail provider · S3/MinIO storage (Phases 4, 7, 9) | — | — | — | ☐ |
| 13 | Decorator | Structural | Cached entitlement repository · progress write-back buffer (Phases 8, 10) | — | [note](java/06-oop-composition-over-inheritance.md) | [lab](lab/src/main/java/com/masternova/java/oop/notify/) | 🔨 |
| 14 | Facade | Structural | `EntitlementService` · `CheckoutService` (Phases 8, 9) | — | — | — | ☐ |
| 15 | Proxy | Structural | Spring's own: `@Transactional` / `@PreAuthorize` / `@Cacheable` proxies · lazy video manifests · playback guard (Phases 1, 8) | — | [note](docs/15-proxy.md) | [lab](lab/src/main/java/com/masternova/patterns/proxy/) | 🔨 |
| 16 | Repository + Unit of Work | Enterprise | Every module's persistence; `@Transactional` is the Unit of Work (Phase 2) | — | — | — | ☐ |
| 17 | Transactional Outbox | Enterprise | State change + event in one transaction, relayed by the worker (Phase 2) | — | — | — | ☐ |
| 18 | Value Object | Enterprise | `Money` (course prices, coupons, revenue splits) · `LectureDuration` (Phases 5, 9) | — | [note](java/01-records-and-value-objects.md) | [lab](lab/src/main/java/com/masternova/java/valueobject/) | 🔨 |
<!-- catalog:end -->

## Patterns Spring uses on you

Spotting these is a common interview talking point. Each one is covered in Phase 1.

| Spring feature | Pattern |
|---|---|
| Dependency injection of an interface | Strategy (the container picks the implementation) |
| `@Transactional`, `@Cacheable`, `@Async` | Proxy (a generated wrapper around your bean) |
| `JdbcTemplate`, `RestClient`, `TransactionTemplate` | Template Method (with callbacks) |
| `SecurityFilterChain`, servlet filters, `HandlerInterceptor` | Chain of Responsibility |
| `ApplicationEventPublisher` + `@EventListener` | Observer |
| `BeanFactory`, `FactoryBean` | Factory |
| `HandlerAdapter`, `HttpMessageConverter` | Adapter |
| `@Bean` singletons | Singleton (container-managed, not the static GoF version) |
