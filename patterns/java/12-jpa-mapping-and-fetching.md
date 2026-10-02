# 12 — JPA Mapping & Fetching: aggregates, value objects, and N+1 measured

> **One-liner:** map an **aggregate** (one root, children inside it, one repository), store
> **value objects** as columns (`@Embeddable` for several, `AttributeConverter` for one), make
> **every association LAZY**, and then decide *per screen* how to load what it needs: an
> **entity graph** for one level of joins and **batch fetching** for the next. Count the
> statements in a test so the N+1 can't come back.

**Roadmap:** tasks 5.2 + 5.3 · **Last updated:** 2026-10-02 · **Prev:** [11 — HMAC & signed tokens](11-hmac-and-signed-tokens.md) · **Builds on:** [10 — Request lifecycle & JPA](10-request-lifecycle-and-jpa.md) (persistence context, entity states, the basic N+1)

**Real code (not a lab copy; this is production):**
- [`catalog/domain/Course.java`](../../backend/api/src/main/java/com/masternova/api/catalog/domain/Course.java): the aggregate root, the `@Embedded Money`, the inverse `sections` side.
- [`Section.java`](../../backend/api/src/main/java/com/masternova/api/catalog/domain/Section.java) / [`Lecture.java`](../../backend/api/src/main/java/com/masternova/api/catalog/domain/Lecture.java): owning sides, `@OrderBy`, `@BatchSize`.
- [`kernel/money/Money.java`](../../backend/kernel/src/main/java/com/masternova/kernel/money/Money.java): a record as an `@Embeddable`.
- [`catalog/infrastructure/LectureDurationConverter.java`](../../backend/api/src/main/java/com/masternova/api/catalog/infrastructure/LectureDurationConverter.java): an auto-applied converter.
- [`CourseRepository.java`](../../backend/api/src/main/java/com/masternova/api/catalog/domain/CourseRepository.java): `@EntityGraph` on a derived query.
- [`V6__catalog.sql`](../../backend/api/src/main/resources/db/migration/V6__catalog.sql): the schema the mapping is validated against.

**Tests (real Postgres, Testcontainers):**
- [`CourseQueryCountIT`](../../backend/api/src/test/java/com/masternova/api/catalog/domain/CourseQueryCountIT.java): every fetch strategy **counted** with Hibernate statistics.
- [`CatalogPersistenceIT`](../../backend/api/src/test/java/com/masternova/api/catalog/domain/CatalogPersistenceIT.java): round trip, value objects, ordering, DB constraints.

**Run:** `cd backend && ./mvnw verify -pl api -am -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='CourseQueryCountIT,CatalogPersistenceIT'`

| # | Section | Priority |
|---|---|---|
| 1 | [Aggregates become mappings](#1-aggregates-become-mappings-) | ⭐⭐⭐ |
| 2 | [Associations: owning side, cascade, orphans, defaults](#2-associations-owning-side-cascade-orphans-defaults-) | ⭐⭐⭐ |
| 3 | [Value objects in JPA: `@Embeddable` vs `AttributeConverter`](#3-value-objects-in-jpa-embeddable-vs-attributeconverter-) | ⭐⭐ |
| 4 | [Collections: bags, sets, order](#4-collections-bags-sets-order-) | ⭐⭐⭐ |
| 5 | [Fetch strategies, measured](#5-fetch-strategies-measured-) | ⭐⭐⭐ |
| 6 | [Counting statements in a test](#6-counting-statements-in-a-test-) | ⭐⭐ |
| 7 | [The database backs the aggregate](#7-the-database-backs-the-aggregate-) | ⭐⭐ |
| 8 | [Common mistakes](#8-common-mistakes-) | ⭐⭐⭐ |
| 9 | [Interview Q&A](#9-interview-qa-) | ⭐⭐⭐ |
| 10 | [30-second recall](#10-30-second-recall) | ⭐⭐⭐ |

---

## 1. Aggregates become mappings ⭐⭐⭐

An **aggregate** is a cluster of objects that change together and keep one set of rules. It has
one **root**; outsiders hold a reference to the root only.

```text
Course  (root: has the repository, the @Version, the rules)
 ├── Section   (inside: no repository, no independent lifecycle)
 │    └── Lecture (inside)
 ├── Money     (value object: columns of the course row)
 └── Category  (another aggregate: referenced, never cascaded)
```

How that shapes the JPA code:

| Aggregate rule | JPA consequence |
|---|---|
| one repository per aggregate | `CourseRepository` only. **No** `SectionRepository`. |
| children live and die with the root | `cascade = ALL` + `orphanRemoval = true` on `Course.sections` and `Section.lectures` |
| invariants are enforced by the root | children's mutators are **package-private**; `Course.addLecture(section, …)` adds the lecture **and** updates `lectureCount` / `totalDuration` in the same call |
| a reference to another aggregate is just a reference | `Course.category` is a `@ManyToOne` with **no cascade** (saving a course must never edit a category) |
| another module's aggregate is not ours to map | `Course.instructorId` is a plain `UUID` column, not a `@ManyToOne User`. identity owns `User`. |
| one consistency boundary | `@Version` on the **root** only. Phase 6's optimistic locking bumps it for any change inside. |

⭐⭐⭐ **Why no `SectionRepository`?** Because `sectionRepository.save(new Lecture(...))` would
add a lecture *without* updating the course's `lectureCount`. The repository shape is how you
stop callers from breaking invariants.

```java
// Course.java — the only way to add a lecture
public Lecture addLecture(Section section, String title, LectureKind kind,
                          boolean preview, LectureDuration duration, UUID assetId) {
  if (section.course() != this) {                         // ⭐ not someone else's section
    throw new IllegalArgumentException("that section belongs to another course");
  }
  Lecture lecture = section.addLecture(title, kind, preview, duration, assetId); // package-private
  lectureCount++;                                         // ⭐ rollups move in the same call
  totalDuration = totalDuration.plus(duration);
  return lecture;
}
```

The getters hand out `Collections.unmodifiableList(...)`, so `course.sections().add(...)` throws
instead of silently bypassing the root.

## 2. Associations: owning side, cascade, orphans, defaults ⭐⭐⭐

**Owning vs inverse side.** In a bidirectional one-to-many, only **one** side writes the foreign
key. The side with the `@JoinColumn` (the `@ManyToOne`) is the **owner**. The side with
`mappedBy` is the **inverse**, a read-only mirror for navigation.

```java
// Section.java — OWNER: this field's value becomes section.course_id
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "course_id")
private Course course;

// Course.java — INVERSE: "the FK is on Section.course"
@OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("position")
private List<Section> sections = new ArrayList<>();
```

⭐⭐⭐ The trap: adding to the inverse list **without** setting the owner writes `course_id =
NULL` (or nothing). That's why `Section`'s constructor takes the course, and only
`Course.addSection` creates sections: both sides are set in one place.

**Cascade** = "when I do X to the parent, do X to the children". `ALL` covers persist, merge,
remove, refresh, detach. So `courses.save(course)` persists 2 sections and 3 lectures with one
call (`CatalogPersistenceIT`).

**`orphanRemoval = true`** = "a child removed from the collection is deleted". It's stronger
than `CascadeType.REMOVE` (which only acts when the *parent* is removed). Phase 6's "delete
section" is just `sections.remove(s)`.

**The defaults you must override:**

| Annotation | JPA default fetch | Use |
|---|---|---|
| `@ManyToOne` | ⭐ **EAGER** | always `fetch = LAZY` |
| `@OneToOne` | ⭐ **EAGER** | `LAZY` (and know that the inverse side of a one-to-one can't be lazy without bytecode enhancement) |
| `@OneToMany` | LAZY | fine |
| `@ManyToMany` | LAZY | fine (and usually model the join table as an entity instead) |
| `@ElementCollection` | LAZY | identity's `roles` is EAGER on purpose: tiny, and needed on every login |

⭐⭐⭐ An EAGER to-one is a **hidden N+1**: load 20 courses with JPQL, and Hibernate fires one
extra query per distinct category *immediately*, whether you need it or not. You can't turn EAGER
off for one query; you can always turn LAZY on for one query. So the mapping is LAZY, and each
screen decides (§5).

## 3. Value objects in JPA: `@Embeddable` vs `AttributeConverter` ⭐⭐

| | `@Embeddable` | `AttributeConverter<X, Y>` |
|---|---|---|
| columns | **many** (Money: `price_minor` + `currency`) | **one** (LectureDuration: `duration_seconds`) |
| queryable by part | yes: `price.amountMinor` in JPQL / Criteria / `Sort` | no: the DB sees an opaque column value |
| where the mapping lives | annotations on the type + `@AttributeOverride` on the field | a converter class |
| records? | ✅ since Hibernate 6.2 (canonical constructor) | ✅ any type |

**Money as an `@Embeddable` record:**

```java
@Embeddable
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {
  public Money {                     // ⭐ compact constructor: runs when HIBERNATE builds it too
    Objects.requireNonNull(currency, "currency");
    if (amountMinor < 0) throw new IllegalArgumentException(...);
  }
}

// Course.java — rename the components to this table's columns
@Embedded
@AttributeOverride(name = "amountMinor", column = @Column(name = "price_minor", nullable = false))
@AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false, length = 3))
private Money price;
```

⭐⭐ Because Hibernate instantiates a record through its **canonical constructor**, a corrupt row
(say a negative price written by hand) fails *on load* instead of becoming a negative price in
memory. A classic mutable embeddable with a no-arg constructor and field injection would not
check anything.

`java.util.Currency` needs no converter: Hibernate maps it to `VARCHAR` by itself. (The column
is `VARCHAR(3)`, not `CHAR(3)`: `ddl-auto=validate` would reject `bpchar` for a string type.)

**Money lives in the kernel** (shared by catalog now and commerce in Phase 9). The kernel is
plain Java, so it depends on `jakarta.persistence-api` as **`optional`**: the annotation is inert
metadata unless a JPA provider reads it, and the dependency isn't passed on to consumers.

**LectureDuration with an auto-applied converter:**

```java
@Converter(autoApply = true)                     // ⭐ every LectureDuration attribute, everywhere
class LectureDurationConverter implements AttributeConverter<LectureDuration, Integer> {
  public Integer convertToDatabaseColumn(LectureDuration d) { return d == null ? null : d.seconds(); }
  public LectureDuration convertToEntityAttribute(Integer s) { return s == null ? null : LectureDuration.ofSeconds(s); }
}
```

⭐⭐ `autoApply` is a **layering** decision, not only a convenience. With
`@Convert(converter = LectureDurationConverter.class)` on the field, the `domain` entity would
import an `infrastructure` class: the arrow would point the wrong way. With `autoApply`, the
entity just declares a `LectureDuration` field, and the converter (in infrastructure) knows about
the domain type. Dependencies point inward.

The spec lets a provider call a converter with `null`. Pass it through and let the column's
`NOT NULL` refuse it, so the converter never has to guess what "absent" means.

## 4. Collections: bags, sets, order ⭐⭐⭐

Hibernate classifies a collection by **how it can identify an element**:

| Java type + mapping | Hibernate calls it | Duplicates? | Order? |
|---|---|---|---|
| `List` (no `@OrderColumn`) | **bag** | allowed | `@OrderBy` sorts on load (SQL `ORDER BY`) |
| `List` + `@OrderColumn` | indexed list | — | a hidden index column stores the position |
| `Set` | set | no | `LinkedHashSet` + `@OrderBy` for order |

We use **`List` + `@OrderBy("position")`**: the position is a real, meaningful column the domain
manages (Phase 6 reorders it), so there's no need for a hidden `@OrderColumn`.

⭐⭐⭐ **`MultipleBagFetchException`.** Try to load sections *and* their lectures in one query:

```java
em.createQuery("select c from Course c join fetch c.sections s join fetch s.lectures", Course.class)
// → MultipleBagFetchException: cannot simultaneously fetch multiple bags
```

The SQL result would be one row per (section × lecture). For a **bag**, Hibernate can't tell "the
same section repeated because it has 5 lectures" from "this section really appears 5 times", so
it refuses. (`CourseQueryCountIT.hibernateRefusesToJoinFetchTwoBagsAtOnce` proves it.)

The tempting fix, changing both to `Set`, **compiles and is worse**: the query now returns
sections × lectures rows (a **cartesian product**). 10 sections × 50 lectures each = 500 rows to
build 60 objects. The right fix is to **not fetch two collections in one query** (§5).

## 5. Fetch strategies, measured ⭐⭐⭐

Scenario: the course page renders the course, its category, 10 sections and 50 lectures.
Numbers from `CourseQueryCountIT` (Hibernate's `prepareStatementCount`) on real Postgres:

| How it's loaded | Statements | Why |
|---|---:|---|
| `findById` + lazy traversal, **no** `@BatchSize` | **13** | course · category · sections · **1 per section** for lectures (10): the N+1 |
| entity graph `{category, sections}`, **no** `@BatchSize` | **11** | 1 join query · still 1 per section for lectures |
| `findById` + lazy traversal, **with** `@BatchSize(64)` | **4** | course · category · sections · **1 batched** lecture query |
| ⭐ entity graph `{category, sections}` **+** `@BatchSize(64)` | **2** | 1 join query · 1 batched lecture query |
| join fetch sections **and** lectures | 💥 | `MultipleBagFetchException` (§4) |

The 11 and 13 rows were measured by temporarily commenting out `@BatchSize` (2026-10-02); the test
keeps the 2 and 4.

⭐⭐⭐ **2 statements, constant in the curriculum size.** A 1-section course also takes 2
(`aSmallCurriculumCostsTheSame`). That's the property to aim for: the number of statements must
not grow with the data.

The tools:

```java
// 1. ENTITY GRAPH — "for this query, also fetch these" (as joins)
@EntityGraph(attributePaths = {"category", "sections"})
Optional<Course> findWithCurriculumBySlug(String slug);

// 2. BATCH FETCHING — "when one proxy/collection of this kind is touched, load many at once"
@OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("position")
@BatchSize(size = 64)                      // → SELECT … FROM lecture WHERE section_id IN (?, ?, …)
private List<Lecture> lectures = new ArrayList<>();
```

| Strategy | Statements | Good for | Watch out |
|---|---|---|---|
| `JOIN FETCH` in JPQL | 1 | one to-one or **one** collection | duplicates the root per child row (Hibernate 6+ de-duplicates entities for you) · one bag max |
| `@EntityGraph` | 1 | same as JOIN FETCH, declared on a repository method | same limits; Spring Data uses it as a **fetch graph** |
| `@BatchSize` | 1 per batch | the **second** level, and to-ones in lists | the batch only covers what's in the persistence context |
| `@Fetch(SUBSELECT)` | 1 | a collection of everything loaded by the previous query | re-runs the original query as a subselect |
| DTO projection (`select new …`, interface projection) | 1 | read-only lists | no entity, no dirty checking: perfect for list pages (Phase 5's browse uses entities + a fetch graph instead, see 5.5) |

**The lazy to-one in a list** (`aLazyToOneInAListIsOneStatementPerDistinctTarget`): 4 courses in 3
categories, touch each course's category → **1 + 3** statements. One per *distinct* category, not
per course, because the persistence context loads each category once (the identity map from note
10 §5). Still N+1-shaped: 20 courses in 12 categories = 13 statements. The browse query (5.5)
fetches the category in the same statement.

## 6. Counting statements in a test ⭐⭐

```java
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = Replace.NONE)       // ⭐ the Testcontainers Postgres, not H2
@Import(TestcontainersConfiguration.class)
class CourseQueryCountIT {
  @Autowired EntityManagerFactory emf;

  Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

  // arrange: save, then flush + clear — an EMPTY persistence context, so loads really hit the DB
  em.flush(); em.clear(); stats.clear();

  renderCoursePage(courses.findWithCurriculumBySlug(slug).orElseThrow());
  assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
}
```

⭐⭐ Two things make the count honest:

1. **`em.clear()` after seeding.** Otherwise the entities are still in the first-level cache and
   the test measures nothing.
2. **The traversal touches what the screen renders** (`renderCoursePage`). Lazy loading only
   happens when something is accessed.

Why not just log the SQL (`spring.jpa.show-sql`)? Logs are read by humans once; an assertion fails
the build every time someone makes an association EAGER or drops the entity graph.

## 7. The database backs the aggregate ⭐⭐

The aggregate enforces its rules in Java, and the schema enforces the ones that must hold even
for code that bypasses Java (a migration, a psql session, a bug):

| Rule | Java | Database |
|---|---|---|
| slug unique | — (can't be checked without a race) | `UNIQUE (slug)` → `DataIntegrityViolationException` |
| section order | `addSection` uses `sections.size()` | `UNIQUE (course_id, position)` |
| price non-negative | `Money`'s compact constructor | `CHECK (price_minor >= 0)` |
| enums | `@Enumerated(STRING)` | `CHECK (status IN (…))` |
| a published course has its sort key | `publish()` stamps `publishedAt` | `CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)` |

⭐⭐ **Uniqueness belongs to the database.** "Check if the slug exists, then insert" has a race
between the check and the insert. The constraint is atomic.

⭐ **Timestamp precision.** Postgres `timestamptz` keeps **microseconds**; `Instant.now()` has
nanoseconds. A `publishedAt` of `…30.123456789Z` comes back as `…30.123456Z`, so the object you
saved and the object you load aren't equal. `Course` truncates to micros when it stamps a time.
It matters in 5.5: the cursor is built from the in-memory value and compared against the DB value.

## 8. Common mistakes ⭐⭐⭐

| Mistake | Symptom | Fix |
|---|---|---|
| leaving `@ManyToOne` EAGER (the default) | an extra query per distinct target on every list, even when unused | `fetch = LAZY` everywhere; fetch per query |
| `FetchType.EAGER` on a collection "to fix" `LazyInitializationException` | every load of the parent loads all children, everywhere, forever | an entity graph on the query that needs them |
| fetch-joining two `List`s | `MultipleBagFetchException` | one level by join, the next by `@BatchSize` |
| switching to `Set` to silence it | cartesian product: rows = sections × lectures | same as above |
| setting only the inverse side (`course.sections().add(s)`) | `course_id` NULL / nothing written | create children through the root, which sets the owner |
| `CascadeType.ALL` on a `@ManyToOne` (course → category) | saving a course edits or deletes a category | cascade only from a parent to its own children |
| a repository per table | callers bypass the root and its invariants | one repository per aggregate |
| `@Convert(converter = …)` on a domain entity | domain depends on infrastructure | `@Converter(autoApply = true)` |
| `Money` as `double` or two loose columns | rounding bugs; currency mix-ups | an `@Embeddable` value object |
| comparing an `Instant` you saved with the one you loaded | flaky equality (nanos vs micros) | truncate to micros when stamping |
| asserting on SQL by reading logs | regressions slip through | assert `Statistics.getPrepareStatementCount()` |

## 9. Interview Q&A ⭐⭐⭐

**Q: What is the N+1 problem and how do you fix it in JPA?**
Loading N parents with one query, then lazily loading each parent's association with one query
*each*: N+1 statements. Fix per query: `JOIN FETCH` / `@EntityGraph` for one level, `@BatchSize`
(or `SUBSELECT`) for the next, or a DTO projection for read-only lists. Then **assert the
statement count** with Hibernate statistics so it stays fixed. Our course page went from 13
statements to 2.

**Q: Why not just make the association EAGER?**
EAGER is global: every query that loads the entity pays for it, needed or not, and JPQL loads
EAGER associations with *separate* queries, which is itself N+1. LAZY + fetch-per-query lets each
screen load exactly what it renders.

**Q: What is `MultipleBagFetchException`?**
Hibernate refuses to join-fetch two `List` collections without index columns (bags) in one query,
because the joined rows multiply and a bag can't be de-duplicated. Don't switch to `Set` (that
produces a cartesian product); fetch the first collection by join and the second with
`@BatchSize` or a second query.

**Q: Owning side vs inverse side?**
The owning side has the foreign key (`@ManyToOne` + `@JoinColumn`) and is what JPA writes. The
inverse side (`mappedBy`) is for navigation only. Changes to just the inverse side aren't
persisted. Keep both in sync with a method on the parent.

**Q: `@Embeddable` or `AttributeConverter` for a value object?**
Converter for a single column (a duration in seconds, an email). Embeddable for several columns
(money = amount + currency), which also keeps each part queryable. Records work for both in
Hibernate 6.2+.

**Q: `cascade = REMOVE` vs `orphanRemoval = true`?**
`REMOVE` deletes children when the parent is deleted. `orphanRemoval` also deletes a child that's
removed from the parent's collection. For an aggregate's children you want both (`ALL` +
`orphanRemoval`).

**Q: Why no repository for `Section`?**
Sections are inside the `Course` aggregate. A separate repository would let callers change a
course's curriculum without the course (no rollup update, no version bump, no invariant checks).
One repository per aggregate root.

**Q: How do you reference an entity owned by another module?**
By id (`UUID instructorId`), not with a JPA association. A `@ManyToOne User` would couple catalog's
mapping to identity's entity and let catalog navigate into identity's internals.

## 10. 30-second recall

- **Aggregate** = root + insides; **one repository per root**; children created and changed
  only through the root (package-private mutators, unmodifiable getters).
- **Associations:** owner = `@ManyToOne` + `@JoinColumn`; inverse = `mappedBy`. **Every to-one
  LAZY** (JPA defaults `@ManyToOne`/`@OneToOne` to EAGER). Cascade only parent → own children;
  `orphanRemoval` for removals.
- **Value objects:** several columns → `@Embeddable` record + `@AttributeOverride`; one column →
  `@Converter(autoApply = true)` (keeps the domain free of infrastructure imports).
- **Bags:** two `List` join fetches → `MultipleBagFetchException`; `Set` → cartesian product.
- **Course page: 13 → 2 statements** = entity graph `{category, sections}` + `@BatchSize` on
  lectures. Constant whatever the curriculum size.
- **Prove it:** `generate_statistics=true`, `flush + clear`, touch what the screen renders,
  assert `getPrepareStatementCount()`.
- **The DB backs the rules:** unique slug, unique positions, CHECKs; timestamps truncated to
  micros.
