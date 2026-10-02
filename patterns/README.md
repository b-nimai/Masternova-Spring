# Patterns — review here first

> Every design pattern used in Masternova, in one table. Read the **note**, open the
> **real class** to see it in production code, and run the **lab** copy to see it on its own.

**Last updated:** 2026-10-02

## How this folder works

| Piece | Where | What it gives you |
|---|---|---|
| **Catalog** | this table | One row per pattern: where it lives in the product and its status |
| **Note** | [`docs/NN-<pattern>.md`](docs/_TEMPLATE.md) | Intent, the Masternova problem, UML, code walkthrough, when *not* to use it, interview Q&A, 30-sec recall |
| **Real class** | `backend/**` | The production class, marked `@DesignPattern(value = …, role = …)` |
| **Lab** | [`lab/`](lab/) | A simplified, runnable, Spring-free copy with a test. `cd patterns/lab && ./mvnw test` |

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

**Status:** `☐` planned · `🔨` in progress · `✅` in real code + note + lab

## Catalog

<!-- catalog:start -->
| # | Pattern | Type | Where in Masternova | Real class | Note | Lab | Status |
|---|---------|------|---------------------|------------|------|-----|--------|
| 1 | Strategy | Behavioral | Payment providers (Phase 9) · auth methods (Phase 3) · ABR transcode ladder (Phase 7) | — | [note](docs/01-strategy.md) | [lab](lab/src/main/java/com/masternova/patterns/strategy/) | 🔨 |
| 2 | State | Behavioral | Course lifecycle (Phase 6) · order state machine (Phase 9) · upload session (Phase 7) | — | — | — | ☐ |
| 3 | Chain of Responsibility | Behavioral | Entitlement policy chain, explicit DENY wins (Phase 8) | — | — | — | ☐ |
| 4 | Command | Behavioral | Undoable curriculum edits (Phase 6) | — | — | — | ☐ |
| 5 | Memento | Behavioral | Snapshot/restore for curriculum undo (Phase 6) | — | — | — | ☐ |
| 6 | Template Method | Behavioral | Pipeline job base class · email templates (Phases 4, 7) | — | — | — | ☐ |
| 7 | Observer | Behavioral | Domain events → outbox relay → handlers (Phases 2, 4) | — | — | — | ☐ |
| 8 | Specification | Enterprise | Catalog filters (JPA `Specification`) · coupon rules · publish gate (Phases 5, 6, 9) | — | — | — | ☐ |
| 9 | Factory Method / Registry | Creational | Job processor registry · email template registry (Phases 4, 7) | — | — | — | ☐ |
| 10 | Builder | Creational | ffmpeg HLS command builder · test data builders (Phases 5, 7) | — | — | — | ☐ |
| 11 | Prototype | Creational | Course duplication (Phase 5) | — | — | — | ☐ |
| 12 | Adapter | Structural | Razorpay gateway · mail provider · S3/MinIO storage (Phases 4, 7, 9) | — | — | — | ☐ |
| 13 | Decorator | Structural | Cached entitlement repository · progress write-back buffer (Phases 8, 10) | — | — | — | ☐ |
| 14 | Facade | Structural | `EntitlementService` · `CheckoutService` (Phases 8, 9) | — | — | — | ☐ |
| 15 | Proxy | Structural | Spring's own: `@Transactional` / `@Cacheable` proxies (Phase 1) | — | — | — | ☐ |
| 16 | Repository + Unit of Work | Enterprise | Every module's persistence; `@Transactional` is the Unit of Work (Phase 2) | — | — | — | ☐ |
| 17 | Transactional Outbox | Enterprise | State change + event in one transaction, relayed by the worker (Phase 2) | — | — | — | ☐ |
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
