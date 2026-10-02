# 10 — Request Lifecycle & JPA Fundamentals

> **One-liner:** a request passes through **filters → DispatcherServlet → interceptors →
> argument binding and validation → your controller → exception advice**, then back out in
> mirror order. Underneath, **JPA/Hibernate** keeps a *persistence context*: one object per row,
> automatic change detection, and lazy loading. That makes it convenient, and the source of the
> two classic production bugs: `LazyInitializationException` and the **N+1 query problem**.

**Roadmap:** task 1.10 · **Last updated:** 2026-10-02 · **Prev:** [09 — Spring AOP & proxies](09-spring-aop-and-proxies.md)
**Learning tests:**
- [`learning/web/RequestLifecycleLearningTest`](../../backend/api/src/test/java/com/masternova/api/learning/web/RequestLifecycleLearningTest.java): records the exact order of filter / interceptor / controller / advice.
- [`learning/jpa/JpaFundamentalsLearningIT`](../../backend/api/src/test/java/com/masternova/api/learning/jpa/JpaFundamentalsLearningIT.java): **real Postgres** via Testcontainers. Hibernate statistics *count* the SQL statements.

**Run:** `cd backend && ./mvnw verify -pl api -am -Dtest=RequestLifecycleLearningTest -Dit.test=JpaFundamentalsLearningIT -Dsurefire.failIfNoSpecifiedTests=false`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know.

| # | Section | Priority |
|---|---|---|
| 1 | [The journey of a request](#1-the-journey-of-a-request-) | ⭐⭐⭐ |
| 2 | [Filter vs interceptor vs aspect vs advice](#2-filter-vs-interceptor-vs-aspect-vs-advice-) | ⭐⭐⭐ |
| 3 | [Binding, validation, responses](#3-binding-validation-responses-) | ⭐⭐ |
| 4 | [JPA in one picture](#4-jpa-in-one-picture-) | ⭐⭐⭐ |
| 5 | [The persistence context and entity states](#5-the-persistence-context-and-entity-states-) | ⭐⭐⭐ |
| 6 | [Relationships and fetching](#6-relationships-and-fetching-) | ⭐⭐⭐ |
| 7 | [`LazyInitializationException` and open-in-view](#7-lazyinitializationexception-and-open-in-view-) | ⭐⭐⭐ |
| 8 | [The N+1 problem, measured](#8-the-n1-problem-measured-) | ⭐⭐⭐ |
| 9 | [Spring Data repositories](#9-spring-data-repositories-) | ⭐⭐ |
| 10 | [Schema, entities, and the test-only migration](#10-schema-entities-and-the-test-only-migration-) | ⭐⭐ |
| 11 | [Common mistakes](#11-common-mistakes-) | ⭐⭐⭐ |
| 12 | [Interview Q&A](#12-interview-qa-) | ⭐⭐⭐ |
| 13 | [30-second recall](#13-30-second-recall) | ⭐⭐⭐ |

---

## 1. The journey of a request ⭐⭐⭐

```text
HTTP ─► Tomcat
         └─► Servlet FILTERS  (Spring Security's chain, logging, CORS, Idempotency-Key in Phase 2…)
              └─► DispatcherServlet ── the front controller of Spring MVC
                   ├─ HandlerMapping: which controller method matches?  (404 if none)
                   ├─ HandlerInterceptor.preHandle      (return false → stop here)
                   ├─ argument resolution: @PathVariable, @RequestParam, @RequestBody (JSON → record via Jackson)
                   │    └─ @Valid → Bean Validation   (fails → MethodArgumentNotValidException; the body never runs)
                   ├─ YOUR CONTROLLER METHOD
                   ├─ return value → JSON (HttpMessageConverter) / ResponseEntity status + headers
                   ├─ exception? → @ExceptionHandler / @RestControllerAdvice → ProblemDetail
                   ├─ HandlerInterceptor.postHandle      (⚠️ skipped if an exception was thrown)
                   └─ HandlerInterceptor.afterCompletion (always: cleanup)
         ◄── back out through the filters (in reverse)
```

These orders are asserted by `RequestLifecycleLearningTest`:

| Scenario | Recorded order |
|---|---|
| success (`theHappyPath…`) | filter before → preHandle → controller → postHandle → afterCompletion → filter after |
| exception (`anExceptionSkipsPostHandle…`) | filter before → preHandle → controller → **advice** → afterCompletion → filter after (no postHandle) |
| invalid body (`invalidInputNeverReachesTheController`) | filter before → preHandle → **advice: validation** → afterCompletion → filter after (no controller!) |

**NestJS → Spring:**

| NestJS | Spring MVC |
|---|---|
| middleware | servlet `Filter` (`OncePerRequestFilter`) |
| guards | Spring Security filters / `@PreAuthorize` |
| interceptors | `HandlerInterceptor`, or AOP aspects |
| pipes (validation, transformation) | Bean Validation (`@Valid`) + argument resolvers / converters |
| exception filters | `@RestControllerAdvice` + `@ExceptionHandler` |
| `@Controller()` + `@Get()` | `@RestController` + `@GetMapping` |

---

## 2. Filter vs interceptor vs aspect vs advice ⭐⭐⭐

| | **Filter** | **HandlerInterceptor** | **AOP aspect** (note 09) | **@RestControllerAdvice** |
|---|---|---|---|---|
| Layer | Servlet (before Spring MVC) | Spring MVC | any Spring bean | Spring MVC |
| Sees | raw request/response; can wrap the body | the chosen handler method | method calls on proxied beans | exceptions from controllers |
| Runs for | **every** request (static files, errors, 404s) | only requests mapped to a controller | only proxied method calls | only controller exceptions |
| Use for | security, CORS, request logging, trace ids, raw-body capture (webhook signatures, Phase 9) | per-endpoint concerns needing handler info (rate limits per route) | transactions, caching, timing, business-level cross-cutting | error → Problem Details (`GlobalExceptionHandler`) |

---

## 3. Binding, validation, responses ⭐⭐

```java
record CreateCourseRequest(@NotBlank String title, @PositiveOrZero long priceMinor) {}   // web/dto/

@PostMapping
ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
  …
  return ResponseEntity.created(URI.create("/api/v1/courses/42")).body(created);   // 201 + Location
}
```

- **Binding:** Jackson turns the JSON body into the record. Records work out of the box.
- **`@Valid`** runs Bean Validation on the bound object **before the method body**. Common
  constraints:
  - `@NotNull`, `@NotBlank`, `@Size`, `@Min`/`@Max`, `@Positive`/`@PositiveOrZero`
  - `@Email`, `@Pattern`, `@Past`/`@Future`
  - `@Valid` on a nested object.
- **Errors:** `MethodArgumentNotValidException` → the advice turns it into a 400 Problem Details
  with an `errors` list (`"priceMinor:PositiveOrZero"`, `"title:NotBlank"`). Our real
  `GlobalExceptionHandler` will do the same (API conventions §1).
- **Validating service-method parameters:** put `@Validated` on the class, and constraints on
  the parameters (it's an AOP proxy again).
- **Responses:**
  - Return DTO records, never entities.
  - Use `ResponseEntity` when you need a status or headers (201 + `Location` on create, 204 on
    delete).

---

## 4. JPA in one picture ⭐⭐⭐

| Piece | What it is |
|---|---|
| **JPA** (`jakarta.persistence`) | the *specification*: `@Entity`, `EntityManager`, JPQL |
| **Hibernate** | the *implementation* that does the work |
| **Spring Data JPA** | generates repositories (`JpaRepository`) on top of the `EntityManager` |
| **Entity** | a class mapped to a table: `@Entity`, `@Id`, columns, relationships |
| **`EntityManager`** | the API: `persist`, `find`, `merge`, `remove`, queries; one per transaction |
| **Persistence context** | the set of managed entities inside one `EntityManager` (§5) |

**Entity requirements**, and why each one exists:

| Requirement | Why | `LearningCourse` |
|---|---|---|
| `@Entity`, `@Id` | mapping + identity | ✅ |
| a no-arg constructor (can be `protected`) | Hibernate instantiates via reflection | `protected LearningCourse() {}` |
| **not `final`**, no `final` methods | Hibernate subclasses entities for lazy proxies (note 09) | ✅ |
| **not a record** | records are final and immutable; entities change over time | records are for DTOs and value objects |
| `@Version` (our convention) | optimistic locking: concurrent edits → 409 in Phase 6 | `private long version` |

---

## 5. The persistence context and entity states ⭐⭐⭐

```text
            persist()                       commit / clear() / detach()
 NEW ─────────────────► MANAGED ◄────────────────────────────────────► DETACHED
(transient)              │  ▲                        merge() returns a MANAGED copy
                         │  │ find() / query
              remove()   ▼  │
                       REMOVED ──(flush)──► DELETE
```

**What MANAGED gives you:**

1. **Identity map / first-level cache:** within one persistence context, one row = **one Java
   object**. `thePersistenceContextReturnsTheSameObjectWithoutASecondQuery` calls `find` twice
   and gets the same object (`isSameAs`) from **1 SQL query**.
2. **Dirty checking:** change a field on a managed entity, and Hibernate compares it with its
   snapshot at **flush** and writes the `UPDATE`. No `save()` needed
   (`dirtyCheckingSavesChangesWithoutCallingSave`, where `@Version` also goes 0 → 1).
3. **Write-behind:** SQL is sent at **flush**, not on each setter call.

**Flush happens** at commit, before a query whose result could depend on pending changes, or on
an explicit `flush()`.

**DETACHED** (after the transaction ends, or after `clear()`/`detach()`): Hibernate no longer
tracks the object. Changes are **ignored** until you `merge()` it, and `merge` returns a
**different, managed copy** (`changesToADetachedEntityAreIgnoredUntilMerged`).

> In a Spring service, the persistence context lives exactly as long as the `@Transactional`
> method (note 09). Load, change and return: the commit flushes the changes.

---

## 6. Relationships and fetching ⭐⭐⭐

```java
// LearningCourse: the INVERSE side (mappedBy points at the owning field)
@OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("position")
private List<LearningSection> sections = new ArrayList<>();

void addSection(String title) { sections.add(new LearningSection(this, title, sections.size())); }  // ⭐ sync both sides

// LearningSection: the OWNING side (holds the course_id foreign key)
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "course_id")
private LearningCourse course;
```

| Concept | Rule |
|---|---|
| **Owning side** | the side with the foreign key (`@ManyToOne` / `@JoinColumn`). Only changes on the owning side are written. |
| `mappedBy` | marks the inverse side; it names the owning *field* |
| **Defaults** ⚠️ | `@OneToMany`/`@ManyToMany` → **LAZY**. `@ManyToOne`/`@OneToOne` → **EAGER**: always set `fetch = LAZY` explicitly. |
| `cascade = ALL` | persist/remove of the parent cascades to children. For true parent-child (course → sections), not shared entities. |
| `orphanRemoval = true` | removing a child from the collection deletes its row |
| helper methods | keep both sides of a bidirectional association in sync in one place (`addSection`) |

**LAZY** means the collection is a placeholder that runs a query when first touched. That's
great, until it's touched at the wrong time (§7) or in a loop (§8).

---

## 7. `LazyInitializationException` and open-in-view ⭐⭐⭐

```java
LearningCourse course = courses.findAll().getFirst();   // sections NOT loaded
em.clear();                                             // persistence context gone (= transaction ended)
course.sections().size();                               // 💥 LazyInitializationException
```

(`touchingALazyCollectionOutsideThePersistenceContextFails`.) In a real app this happens when a
service returns an entity and the **controller or Jackson** touches a lazy field after the
transaction has ended.

**Open Session in View (OSIV)** "fixes" it by keeping the persistence context open for the
whole HTTP request, so lazy loads succeed anywhere, even during JSON rendering. Spring Boot
enables it by default (with a startup warning). **We turned it off** (`spring.jpa.open-in-view:
false`, already in `application.yaml`) because:

- it holds a **DB connection for the entire request**, including slow serialisation and network
  writes (a scarce resource: note 07 §9);
- it hides N+1 queries, which fire silently during rendering;
- the data access the controller depends on is invisible.

**The correct fix:** the service fetches **exactly what the response needs**, inside the
transaction, and returns **DTOs**:

- JOIN FETCH / `@EntityGraph` (§8);
- **DTO projections** (`select new …CourseSummary(c.id, c.title) from …`, or interface
  projections), which load only the columns you show.

---

## 8. The N+1 problem, measured ⭐⭐⭐

```java
List<LearningCourse> all = courses.findAll();                              // 1 query
all.forEach(c -> c.sections().size());                                     // + 1 query PER course
// theNPlusOneProblem: 10 courses → 1 + 10 = 11 queries. 100 courses on a page → 101.
```

Each lazy collection fires its own `SELECT … WHERE course_id = ?`. It's invisible in code
review, harmless in dev with 3 rows, and a latency disaster in production. The test **counts**
it with Hibernate statistics (`getPrepareStatementCount()`).

**Fixes** (both measured at **1 query**):

```java
@Query("select distinct c from LearningCourse c left join fetch c.sections order by c.id")
List<LearningCourse> findAllWithSections();            // joinFetchLoadsEverythingInOneQuery

@EntityGraph(attributePaths = "sections")
@Query("select c from LearningCourse c order by c.id")
List<LearningCourse> findAllWithSectionsGraph();       // anEntityGraphDoesTheSame
```

| Fix | Trade-off |
|---|---|
| **JOIN FETCH** (JPQL) | explicit; one query; `distinct` avoids duplicate parents |
| **`@EntityGraph`** | same result, declared on the repository method |
| **batch fetching** (`hibernate.default_batch_fetch_size=50`, or `@BatchSize`) | N+1 becomes 1 + N/50: a good global safety net |
| **DTO projection** | the fastest; only the columns you need |

**Gotchas:**

- **JOIN FETCH + pagination:** fetching a collection while paging makes Hibernate paginate **in
  memory** (warning `HHH90003004`). Page the parent ids first, then fetch the children for those
  ids. (Phase 5's keyset pagination does exactly this.)
- **Fetching two `List` collections at once** → `MultipleBagFetchException`. Use `Set`, or
  separate queries.
- Detect N+1 in tests: assert statement counts, as this test does. Phase 5 adds the same check
  for the catalog.

---

## 9. Spring Data repositories ⭐⭐

```java
interface LearningCourseRepository extends JpaRepository<LearningCourse, Long> {
  List<LearningCourse> findByTitleContainingIgnoreCaseOrderByTitle(String fragment);   // derived query
  @Query("…") List<LearningCourse> findAllWithSections();                              // explicit JPQL
}
```

- **The implementation is generated**: a proxy, again (note 09).
- **Free methods:** `findById` (returns `Optional`, note 05), `findAll`, `save`, `saveAll`,
  `delete`, `count`, `existsById`, paging and sorting.
- **Derived queries:** the method name *is* the query
  (`derivedQueriesComeFromTheMethodName` → `"Course 1", "Course 10"`). Great for simple lookups;
  switch to `@Query` once names get long.
- **`save()`:** if the entity is new (id/version null), it calls `persist`; otherwise `merge`.
  On an already-managed entity you don't need `save` at all (dirty checking, §5).
- **Paging:** `Pageable` / `Page<T>` is OFFSET-based and runs a `count(*)`. Our API uses **keyset**
  cursors (ADR-0015, the `Page<T>` from note 04), built on `Window`/`ScrollPosition` or a
  custom query in Phase 5.
- **Keep repositories behind the module boundary:** `infrastructure/` or `domain/`, never
  exposed to other modules (CLAUDE.md §3).

---

## 10. Schema, entities, and the test-only migration ⭐⭐

- **Flyway owns the schema** (`V<n>__*.sql`), and Hibernate only **validates** it
  (`ddl-auto: validate`). Never `update`/`create` in a real environment.
- **Postgres doesn't index foreign keys automatically.** Every `@ManyToOne` column used in
  lookups needs an index (`learning_section_course_id_idx`).
- **The test-only migration trick:** the learning entities need tables, but must never exist in
  production. `src/test/resources/db/migration/V1000__learning_jpa_tables.sql` sits on the
  **test classpath only**, at the same `db/migration` location, so Flyway applies it in test
  databases and the application jar never contains it.

  > 🐛 **How we got here:** the first attempt isolated the learning entities in a separate
  > package with their own `@SpringBootConfiguration` and `create-drop`. Spring Modulith's
  > runtime auto-configuration then refused to start without the application's
  > `@SpringBootApplication`, and slice exclusions didn't remove it. Working *with* the
  > framework (the real app class + a test migration) was simpler and more realistic.

- **Entity `equals`/`hashCode`:** don't use all fields (they change), and don't rely on the
  generated id (it's null before persist). Either compare by id with null-safety and a constant
  `hashCode`, or don't put entities in hash sets at all. Never Lombok `@Data` on entities.

---

## 11. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | § |
|---|---|---|
| business logic in filters/interceptors | services; filters for cross-cutting HTTP concerns | 2 |
| cleanup in `postHandle` | `afterCompletion` (always runs) | 1 |
| returning entities from controllers | DTO records | 3, 7 |
| OSIV on to "fix" lazy loading | fetch what you need in the service, return DTOs | 7 |
| `@ManyToOne` left at its EAGER default | `fetch = LAZY` explicitly | 6 |
| looping over parents and touching a lazy collection | JOIN FETCH / `@EntityGraph` / batch size / projection | 8 |
| JOIN FETCH + `Pageable` | page the ids first, then fetch | 8 |
| calling `save()` on a managed entity "to be safe" | rely on dirty checking inside the transaction | 5, 9 |
| modifying a detached entity and expecting it saved | load it in the transaction, or `merge` | 5 |
| only updating the inverse side of a relationship | update the owning side; use helper methods | 6 |
| `ddl-auto: update` | Flyway migrations + `validate` | 10 |
| no index on FK columns | create it in the migration | 10 |
| `final` entity classes, or records as entities | plain non-final classes | 4 |

---

## 12. Interview Q&A ⭐⭐⭐

**Q1. Walk through how Spring MVC handles a request.**
Filters → DispatcherServlet → HandlerMapping → interceptor `preHandle` → argument resolution,
conversion and validation → controller → return-value handling (JSON) → exception resolvers /
`@ControllerAdvice` on error → `postHandle` (success only) → `afterCompletion` → filters unwind.

**Q2. Filter vs interceptor?**
Filters are servlet-level: they see every request and run before Spring MVC. Interceptors are
Spring MVC-level, know the handler, and only run for mapped requests. Security is a filter.

**Q3. What is the persistence context / first-level cache?**
The set of managed entities in one `EntityManager` (one transaction). It guarantees one object
per row, does dirty checking at flush, and writes SQL behind your back.

**Q4. Entity states?**
Transient (new), managed (tracked), detached (no longer tracked), removed. `merge` copies a
detached entity's state onto a managed instance and returns that instance.

**Q5. What is the N+1 problem? How do you detect and fix it?**
One query for N parents, plus one per parent for a lazy association. Detect it with SQL logging
or statement counts in tests. Fix it with JOIN FETCH, `@EntityGraph`, batch fetching, or DTO
projections.

**Q6. What causes `LazyInitializationException`? Is OSIV the fix?**
Touching a lazy association after the persistence context closed. OSIV keeps it open for the
whole request but holds connections and hides N+1. Fetch what you need in the service instead.

**Q7. LAZY vs EAGER defaults?**
`*ToMany` is LAZY. `*ToOne` is EAGER, so set it to LAZY. EAGER can't be undone per query, while
LAZY can be fetched eagerly when needed.

**Q8. `persist` vs `merge`? What does Spring Data's `save` do?**
`persist` makes a new instance managed. `merge` copies state onto a managed copy. `save` uses
`persist` for new entities and `merge` otherwise.

**Q9. Owning side of a relationship?**
The side holding the foreign key (`@ManyToOne`/`@JoinColumn`). Only it is used to write the
association. `mappedBy` marks the inverse side.

**Q10. When does Hibernate flush?**
At commit, before queries that might be affected by pending changes, and on an explicit
`flush()`.

---

## 13. 30-second recall

- **The request path:** filters → DispatcherServlet → `preHandle` → bind + `@Valid` →
  controller → advice on error → `postHandle` (success only) → `afterCompletion` → filters.
  Invalid input never reaches the controller.
- **Where code goes:**
  - Filter: security, CORS, trace ids, raw body.
  - Interceptor: handler-aware concerns.
  - Aspect: bean methods.
  - Advice: exceptions → ProblemDetail.
- **JPA stack:** JPA is the spec, Hibernate does the work, Spring Data writes the repositories.
  Entities are non-final, have a no-arg constructor, a `@Version`, and are never records.
- **Persistence context:**
  - One object per row (first-level cache).
  - Dirty checking at flush, so no `save()` is needed for managed entities.
  - Detached changes are ignored until `merge`.
- **Relationships:** the owning side holds the FK; `mappedBy` marks the inverse. Make
  `*ToOne` LAZY. Use helper methods to keep both sides in sync.
- **Lazy loading:** `LazyInitializationException` means you touched a lazy field after the
  transaction. Keep OSIV off; fetch in the service and return DTOs.
- **N+1:** measured at 1 + 10 = 11 queries; JOIN FETCH or `@EntityGraph` brings it to 1. Use a
  batch size as a safety net. Never JOIN FETCH while paging.
- **Schema:** Flyway owns it and Hibernate validates. Index your FKs. Test-only tables go in
  `src/test/resources/db/migration`.
- **Next:** Phase 2, the platform kernel: the outbox, idempotency keys and the error model, built
  on everything in notes 01–10.
