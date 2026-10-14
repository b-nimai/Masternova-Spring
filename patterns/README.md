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
| 1 | Strategy | Behavioral | ABR transcode ladder: `StandardLadder` (prod) / `FastLadder` (CI) by property, never-upscale as a shared default method (Phase 7) · entitlement policies: each access rule an interchangeable Strategy over one context, composed by the chain (Phase 8) · payment providers (Phase 9) · Spring's own: `DelegatingPasswordEncoder` picks the hashing algorithm by the `{id}` prefix (Phase 3) · Google sign-in as a second auth method (3.6, deferred) | `com.masternova.worker.pipeline.ffmpeg.LadderProfile`<br>`com.masternova.worker.pipeline.ffmpeg.StandardLadder`<br>`com.masternova.worker.pipeline.ffmpeg.FastLadder`<br>`com.masternova.api.entitlement.domain.EntitlementPolicy` | [note](docs/01-strategy.md) | [lab](lab/src/main/java/com/masternova/patterns/strategy/) | ✅ |
| 2 | State | Behavioral | Course lifecycle: sealed `CourseState`, default methods throw, each state overrides only its legal events (5 overrides = the diagram); gated edges re-run the publish gate (Phase 6) · upload session: PERFORM / REPLAY / 409 per event, an effective `Expired` from the clock, replay + row lock = idempotent complete (Phase 7) · order state machine (Phase 9) | `com.masternova.api.catalog.domain.CourseState`<br>`com.masternova.api.media.domain.UploadState`<br>`com.masternova.api.media.domain.UploadSession` | [note](docs/02-state.md) | [lab](lab/src/main/java/com/masternova/patterns/state/) | ✅ |
| 3 | Chain of Responsibility | Behavioral | Entitlement policy chain: 7 `@Order` policies vote Allow / Deny / Abstain, every one runs, **explicit DENY wins**, closed by default, a throwing policy is a deny (Phase 8) · Spring's own: the `SecurityFilterChain` (bearer-token filter → authorization), fed by every module's `PublicEndpoints` (Phase 3) · Angular's `HttpInterceptorFn` chain (Phase 3) | `com.masternova.api.entitlement.domain.PolicyChain`<br>`com.masternova.api.entitlement.domain.EntitlementPolicy` | [note](docs/03-chain-of-responsibility.md) | [lab](lab/src/main/java/com/masternova/patterns/chain/) | ✅ |
| 4 | Command | Behavioral | Curriculum edits as 11 sealed records (`{"kind": …}` JSON, one route); `applyTo` performs the edit and RETURNS ITS INVERSE; removals' inverses carry a Memento (Phase 6); `ATTACH_MEDIA` gives a lecture its video, undoable like the rest (Phase 7) | `com.masternova.api.catalog.domain.CurriculumCommand` | [note](docs/04-command.md) | [lab](lab/src/main/java/com/masternova/patterns/command/) | ✅ |
| 5 | Memento | Behavioral | Curriculum undo: a removal captures a `SectionSnapshot` before the delete, carried by the inverse command and kept by the `course_edit` history; restores the same ids. Whole-curriculum snapshot stack built (lab), measured (7.5 KB vs ≤ 0.9 KB per edit) and not shipped: ADR-0011 (Phase 6) | `com.masternova.api.catalog.domain.SectionSnapshot`<br>`com.masternova.api.catalog.domain.CourseEditLog` | [note](docs/05-memento.md) | [lab](lab/src/main/java/com/masternova/patterns/memento/) | ✅ |
| 6 | Template Method | Behavioral | Email templates: a final `render` skeleton with layout, plaintext twin and an unsubscribe-link invariant (Phase 4) · pipeline jobs: a final `run` with an absolute work dir, commit markers uploaded last, transactional completion and cleanup in `finally`; four processors fill in one step (Phase 7) | `com.masternova.worker.notification.template.EmailTemplate`<br>`com.masternova.worker.notification.template.VerifyEmailTemplate`<br>`com.masternova.worker.notification.template.WelcomeEmailTemplate`<br>`com.masternova.worker.pipeline.job.JobProcessor`<br>`com.masternova.worker.pipeline.processor.TranscodeProcessor` | [note](docs/06-template-method.md) | [lab](lab/src/main/java/com/masternova/java/oop/template/) | ✅ |
| 7 | Observer | Behavioral | Domain events: `EventPublisher` → `@TransactionalEventListener` observers; outbox handlers (Phases 2, 4) | `com.masternova.api.platform.events.TransactionalEventPublisher` | [note](docs/07-observer.md) | [lab](lab/src/main/java/com/masternova/patterns/observer/) | ✅ |
| 8 | Specification | Enterprise | Catalog filters: 9 Spring Data `Specification` leaves, one composition point, visibility written once and pushed into SQL with an in-memory twin proven equal (Phase 5) · publish gate: coded requirements that explain themselves (Phase 6) · coupon eligibility (Phase 9) | `com.masternova.api.catalog.domain.CourseSpecifications`<br>`com.masternova.api.catalog.application.CourseSearch`<br>`com.masternova.api.catalog.domain.Viewer`<br>`com.masternova.api.catalog.domain.PublishGate` | [note](docs/08-specification.md) | [lab](lab/src/main/java/com/masternova/patterns/specification/) | ✅ |
| 9 | Factory Method / Registry | Creational | Outbox handler registry: event type → `OutboxHandler` (Phase 4) · job processor registry: every `JobType` exactly once, checked at boot (Phase 7) | `com.masternova.messaging.outbox.OutboxRelay`<br>`com.masternova.worker.pipeline.job.JobProcessorRegistry` | [note](docs/09-factory-method-registry.md) | [lab](lab/src/main/java/com/masternova/patterns/registry/) | ✅ |
| 10 | Builder | Creational | Test data builder for the Course aggregate (`aCourse()…build()` through the real factory and methods), used by every catalog test (Phase 5) · ffmpeg HLS command: ~40 ordered arguments, GOP/segment rule in `build()`, a `List<String>` never a shell string (Phase 7) | `com.masternova.api.catalog.domain.CourseBuilder`<br>`com.masternova.worker.pipeline.ffmpeg.FfmpegCommand` | [note](docs/10-builder.md) | [lab](lab/src/main/java/com/masternova/patterns/builder/) | ✅ |
| 11 | Prototype | Creational | Course duplication: copy constructors deep-copy the curriculum, reset history, share immutables and media asset ids; one transaction behind an `Idempotency-Key` (Phase 5) | `com.masternova.api.catalog.domain.Course` | [note](docs/11-prototype.md) | [lab](lab/src/main/java/com/masternova/patterns/prototype/) | ✅ |
| 12 | Adapter | Structural | Mail provider: SMTP / Resend behind `MailProvider`, vendor failures translated to our permanent/temporary contract (Phase 4) · S3 storage: `ObjectStorage` over the AWS SDK v2, no interface (one vendor API), `S3Exception` → our temporary / no-such-upload / permanent contract; SeaweedFS in dev (Phase 7) · Razorpay gateway (Phase 9) | `com.masternova.worker.notification.mail.MailProvider`<br>`com.masternova.worker.notification.mail.SmtpMailProvider`<br>`com.masternova.worker.notification.mail.ResendMailProvider`<br>`com.masternova.storage.ObjectStorage` | [note](docs/12-adapter.md) | [lab](lab/src/main/java/com/masternova/patterns/adapter/) | ✅ |
| 13 | Decorator | Structural | Cached entitlement repository: Redis cache-aside around the JDBC one, same interface; caches the row (not the decision) and negatives, evicts after commit, fails open to Postgres (Phase 8) · progress write-back buffer (Phase 10) | `com.masternova.api.entitlement.infrastructure.CachedEntitlementRepository`<br>`com.masternova.api.entitlement.infrastructure.JdbcEntitlementRepository` | [note](docs/13-decorator.md) | [lab](lab/src/main/java/com/masternova/patterns/decorator/) | ✅ |
| 14 | Facade | Structural | `EntitlementService`: one entry point over catalog's facts, the cached row and the policy chain; owns the sequence and the transaction, never the rules (Phase 8) · `CheckoutService` (Phase 9) | `com.masternova.api.entitlement.application.EntitlementService` | [note](docs/14-facade.md) | [lab](lab/src/main/java/com/masternova/patterns/facade/) | ✅ |
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
