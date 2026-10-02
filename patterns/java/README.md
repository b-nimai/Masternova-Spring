# Java study notes

One detailed note per topic, each paired with **real, tested code** in
[`../lab/src/main/java/com/masternova/java/`](../lab/src/main/java/com/masternova/java/).

Priority marks used in every note: **⭐⭐⭐** must know (interviews, and the bugs that come from
not knowing) · **⭐⭐** use daily · **⭐** good to know. Code comments starting with `// ⭐` mark
the lines worth remembering.

| # | Topic | Note | Code | Roadmap | Status |
|---|---|---|---|---|---|
| 01 | Records & Value Objects: `equals`/`hashCode`, immutability, money done right | [01-records-and-value-objects.md](01-records-and-value-objects.md) | `java/valueobject/` (`Money`, `LectureDuration`) | 1.1 | ✅ |
| 02 | Sealed types & pattern-matching `switch`: exhaustiveness, record patterns, sealed vs Strategy vs Visitor | [02-sealed-types-and-pattern-matching.md](02-sealed-types-and-pattern-matching.md) | `java/sealed/` (`PaymentOutcome`, `CouponRule`, `LectureContent`, `Expr`) | 1.2 | ✅ |
| 03 | Collections, Streams & Collectors: HashMap internals, LRU, CME, comparators, lazy pipelines, `groupingBy`/`toMap`/`teeing`, gatherers | [03-collections-and-streams.md](03-collections-and-streams.md) | `java/streams/` (`CatalogQueries`, `CollectionsTour`) | 1.3 | ✅ |
| 04 | Generics: invariance, wildcards & PECS, bounds (`Comparable<? super T>`), erasure, raw types, type tokens | [04-generics.md](04-generics.md) | `java/generics/` (`Result`, `Page`, `Registry`, `Ranking`, `TypedSettings`) | 1.4 | ✅ |
| 05 | Exceptions & `Optional`: checked vs unchecked, `finally` traps, try-with-resources, custom exceptions → HTTP, `InterruptedException`, `Optional` rules | [05-exceptions-and-optional.md](05-exceptions-and-optional.md) | `java/exceptions/` (`MasternovaException`, `ProblemMapper`, `CourseImporter`, `CourseLookup`, `Retry`) | 1.5 | ✅ |
| 06 | OOP in production: fragile base class, decorators, Template Method, invariants, dispatch traps, SOLID in code | [06-oop-composition-over-inheritance.md](06-oop-composition-over-inheritance.md) | `java/oop/` (`CountingTagSet`, `Channel` + decorators, `EmailTemplate`, `Cart`, `DispatchTraps`) | 1.6 | ✅ |
| 07 | Concurrency: races & JMM, atomics/locks/CAS, idempotency under concurrency, executors, `CompletableFuture`, virtual threads, `ScopedValue`, deadlock | [07-concurrency-and-virtual-threads.md](07-concurrency-and-virtual-threads.md) | `java/concurrency/` (`Counters`, `Cohort`, `WebhookProcessor`, `QuoteService`, `VirtualThreads`, `OutboxRelay`) | 1.7 | ✅ |
| 08 | Spring IoC & DI (with NestJS mapping): bean declaration, constructor injection, resolution, scopes, lifecycle, full vs lite `@Configuration`, conditions & auto-config, `@ConfigurationProperties` | [08-spring-ioc-and-di.md](08-spring-ioc-and-di.md) | `backend/api/.../learning/ioc/` learning tests + real `MasternovaProperties` | 1.8 | ✅ |
| 09 | Spring AOP & proxies: JDK vs CGLIB, aspects, self-invocation, `@Transactional` rollback & propagation, silent-failure checklist | [09-spring-aop-and-proxies.md](09-spring-aop-and-proxies.md) | `backend/api/.../learning/aop/` + pattern lab `patterns/proxy/` | 1.9 | ✅ |
| 10 | Request lifecycle & JPA fundamentals | — | — | 1.10 | ☐ |

**Run all study code:** `cd patterns/lab && ./mvnw test`
**Experiment:** `./mvnw -q compile && jshell --class-path target/classes` (see each note's jshell section)
