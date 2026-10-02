# Patterns & Java — study hub

> Your study material for this project. **Java notes** teach the language, **pattern notes**
> teach the designs, and every note is paired with real, tested code you can run and change.

**Last updated:** 2026-10-02

## Where to start

| Order | Read | Why |
|---|---|---|
| 1 | [Java notes](java/README.md), in number order | The language and Spring features every pattern below is built from (01–07 Java, 08–10 Spring) |
| 2 | [Angular notes](angular/README.md) | The frontend side: signals, components, RxJS, routing, forms |
| 2b | [DevOps notes](devops/README.md) | Images, CI/CD, Kubernetes: each backed by this repo's real files and measurements |
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
| **DevOps notes** | [`devops/NN-<topic>.md`](devops/README.md) | One DevOps topic in depth, built around the Dockerfiles, compose, CI and (later) Helm/Terraform in this repo |
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
| 1 | Strategy | Behavioral | Payment providers (Phase 9) · ABR transcode ladder (Phase 7) · Spring's own: `DelegatingPasswordEncoder` picks the hashing algorithm by the `{id}` prefix (Phase 3) · Google sign-in as a second auth method (3.6, deferred) | — | [note](docs/01-strategy.md) | [lab](lab/src/main/java/com/masternova/patterns/strategy/) | 🔨 |
| 2 | State | Behavioral | Course lifecycle (Phase 6) · order state machine (Phase 9) · upload session (Phase 7) | — | — | — | ☐ |
| 3 | Chain of Responsibility | Behavioral | Entitlement policy chain, explicit DENY wins (Phase 8) · Spring's own: the `SecurityFilterChain` (bearer-token filter → authorization), fed by every module's `PublicEndpoints` (Phase 3) · Angular's `HttpInterceptorFn` chain (Phase 3) | — | — | — | ☐ |
| 4 | Command | Behavioral | Undoable curriculum edits (Phase 6) | — | — | — | ☐ |
| 5 | Memento | Behavioral | Snapshot/restore for curriculum undo (Phase 6) | — | — | — | ☐ |
| 6 | Template Method | Behavioral | Email templates: a final `render` skeleton with layout, plaintext twin and an unsubscribe-link invariant (Phase 4) · pipeline job base class (Phase 7) | `com.masternova.worker.notification.template.EmailTemplate`<br>`com.masternova.worker.notification.template.VerifyEmailTemplate`<br>`com.masternova.worker.notification.template.WelcomeEmailTemplate` | [note](docs/06-template-method.md) | [lab](lab/src/main/java/com/masternova/java/oop/template/) | ✅ |
| 7 | Observer | Behavioral | Domain events: `EventPublisher` → `@TransactionalEventListener` observers; outbox handlers (Phases 2, 4) | `com.masternova.api.platform.events.TransactionalEventPublisher` | [note](docs/07-observer.md) | [lab](lab/src/main/java/com/masternova/patterns/observer/) | ✅ |
| 8 | Specification | Enterprise | Catalog filters: 9 Spring Data `Specification` leaves, one composition point, visibility written once and pushed into SQL with an in-memory twin proven equal (Phase 5) · publish gate · coupon eligibility (Phases 6, 9) | `com.masternova.api.catalog.domain.CourseSpecifications`<br>`com.masternova.api.catalog.application.CourseSearch`<br>`com.masternova.api.catalog.domain.Viewer` | [note](docs/08-specification.md) | [lab](lab/src/main/java/com/masternova/patterns/specification/) | ✅ |
| 9 | Factory Method / Registry | Creational | Outbox handler registry: event type → `OutboxHandler` (Phase 4) · job processor registry (Phase 7) | `com.masternova.messaging.outbox.OutboxRelay` | [note](docs/09-factory-method-registry.md) | [lab](lab/src/main/java/com/masternova/patterns/registry/) | ✅ |
| 10 | Builder | Creational | Test data builder for the Course aggregate (`aCourse()…build()` through the real factory and methods), used by every catalog test (Phase 5) · ffmpeg HLS command builder (Phase 7) | `com.masternova.api.catalog.domain.CourseBuilder` | [note](docs/10-builder.md) | [lab](lab/src/main/java/com/masternova/patterns/builder/) | ✅ |
| 11 | Prototype | Creational | Course duplication: copy constructors deep-copy the curriculum, reset history, share immutables and media asset ids; one transaction behind an `Idempotency-Key` (Phase 5) | `com.masternova.api.catalog.domain.Course` | [note](docs/11-prototype.md) | [lab](lab/src/main/java/com/masternova/patterns/prototype/) | ✅ |
| 12 | Adapter | Structural | Mail provider: SMTP / Resend behind `MailProvider`, vendor failures translated to our permanent/temporary contract (Phase 4) · Razorpay gateway · S3/MinIO storage (Phases 7, 9) | `com.masternova.worker.notification.mail.MailProvider`<br>`com.masternova.worker.notification.mail.SmtpMailProvider`<br>`com.masternova.worker.notification.mail.ResendMailProvider` | [note](docs/12-adapter.md) | [lab](lab/src/main/java/com/masternova/patterns/adapter/) | ✅ |
| 13 | Decorator | Structural | Cached entitlement repository · progress write-back buffer (Phases 8, 10) | — | [note](java/06-oop-composition-over-inheritance.md) | [lab](lab/src/main/java/com/masternova/java/oop/notify/) | 🔨 |
| 14 | Facade | Structural | `EntitlementService` · `CheckoutService` (Phases 8, 9) | — | — | — | ☐ |
| 15 | Proxy | Structural | Spring's own: `@Transactional` / `@PreAuthorize` / `@Cacheable` proxies · lazy video manifests · playback guard (Phases 1, 8) | — | [note](docs/15-proxy.md) | [lab](lab/src/main/java/com/masternova/patterns/proxy/) | 🔨 |
| 16 | Repository + Unit of Work | Enterprise | Outbox + idempotency persistence behind interfaces; `@Transactional` + persistence context is the Unit of Work (Phase 2) | `com.masternova.messaging.outbox.OutboxRepository`<br>`com.masternova.messaging.outbox.JdbcOutboxRepository`<br>`com.masternova.api.platform.idempotency.JdbcIdempotencyStore` | [note](docs/16-repository-unit-of-work.md) | [lab](lab/src/main/java/com/masternova/patterns/repository/) | ✅ |
| 17 | Transactional Outbox | Enterprise | Event row committed with the state change; relay claims with `SKIP LOCKED`, retries with backoff (Phase 2; since Phase 4 in the shared `messaging` module, relayed by the worker) | `com.masternova.messaging.outbox.JdbcOutboxWriter`<br>`com.masternova.messaging.outbox.OutboxRelay` | [note](docs/17-transactional-outbox.md) | [lab](lab/src/main/java/com/masternova/patterns/outbox/) | ✅ |
| 18 | Value Object | Enterprise | `Money` in the kernel, mapped as a JPA `@Embeddable` (course prices; coupons and revenue splits in Phase 9) · `LectureDuration`, one column through an auto-applied `AttributeConverter` (Phase 5) | `com.masternova.kernel.money.Money`<br>`com.masternova.api.catalog.domain.LectureDuration` | [note](java/01-records-and-value-objects.md) | [lab](lab/src/main/java/com/masternova/java/valueobject/) | ✅ |
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
