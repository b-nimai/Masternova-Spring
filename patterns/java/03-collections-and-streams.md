# 03 — Collections, Streams & Collectors

> **One-liner:** pick the right **collection** for each job (`List`, `Set`, `Map`, `Deque`) and
> know its cost. Then express "filter, transform, group, summarise" as a **stream pipeline**:
> source → lazy intermediate steps → one terminal operation, usually a **collector**.

**Roadmap:** task 1.3 · **Last updated:** 2026-10-02 · **Prev:** [02 — Sealed types](02-sealed-types-and-pattern-matching.md)
**Code:** [`lab/.../java/streams/`](../lab/src/main/java/com/masternova/java/streams/): `CatalogQueries` (streams), `CollectionsTour` (Map API & co.), `Course`, `Sale`, `CatalogData`
**Tests:** [`lab/.../java/streams/`](../lab/src/test/java/com/masternova/java/streams/) · **Run:** `cd patterns/lab && ./mvnw test -Dtest='com.masternova.java.streams.*Test'`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know. In code, `// ⭐` marks the lines to remember.

| # | Section | Priority |
|---|---|---|
| 1 | [The collections map](#1-the-collections-map-) | ⭐⭐⭐ |
| 2 | [Immutable, unmodifiable, mutable](#2-immutable-unmodifiable-mutable-) | ⭐⭐⭐ |
| 3 | [The Map API and HashMap internals](#3-the-map-api-and-hashmap-internals-) | ⭐⭐⭐ |
| 4 | [Modifying while iterating](#4-modifying-while-iterating-) | ⭐⭐⭐ |
| 5 | [`Comparable` and `Comparator`](#5-comparable-and-comparator-) | ⭐⭐⭐ |
| 6 | [Lambdas, functional interfaces, method references](#6-lambdas-functional-interfaces-method-references-) | ⭐⭐⭐ |
| 7 | [How a stream pipeline runs](#7-how-a-stream-pipeline-runs-) | ⭐⭐⭐ |
| 8 | [Stream operations cheat sheet](#8-stream-operations-cheat-sheet-) | ⭐⭐ |
| 9 | [Collectors](#9-collectors-) | ⭐⭐⭐ |
| 10 | [Primitive streams](#10-primitive-streams-) | ⭐⭐ |
| 11 | [Streams vs loops](#11-streams-vs-loops-) | ⭐⭐⭐ |
| 12 | [Parallel streams, gatherers, type witnesses](#12-parallel-streams-gatherers-type-witnesses-) | ⭐ |
| 13 | [Walkthrough of the code](#13-walkthrough-of-the-code-) | ⭐⭐ |
| 14 | [Where this goes in Spring / Masternova](#14-where-this-goes-in-spring--masternova-) | ⭐⭐⭐ |
| 15 | [Common mistakes](#15-common-mistakes-) | ⭐⭐⭐ |
| 16 | [Interview Q&A](#16-interview-qa-) | ⭐⭐⭐ |
| 17 | [Play with it in `jshell`](#17-play-with-it-in-jshell-) | ⭐ |
| 18 | [30-second recall](#18-30-second-recall) | ⭐⭐⭐ |

---

## 1. The collections map ⭐⭐⭐

```text
Iterable
 └── Collection
      ├── List      ordered, index-based, duplicates OK        ArrayList · LinkedList
      ├── Set       no duplicates                              HashSet · LinkedHashSet · TreeSet
      └── Queue
           └── Deque   both ends: stack AND queue              ArrayDeque · LinkedList
Map (not a Collection)  key → value, unique keys               HashMap · LinkedHashMap · TreeMap
```

Java 21 added **`SequencedCollection` / `SequencedMap`** for anything with a defined order. They
provide `getFirst()`, `getLast()`, `addFirst()`, `removeLast()`, and `reversed()`
(`CollectionsTour.newestAndOldest` uses them).

**Program to the interface.** Declare `List<Course> courses = new ArrayList<>();`, not
`ArrayList<Course>`. Callers depend on the contract, and you can swap the implementation.

### Which one? ⭐⭐⭐

| Need | Use | Order | Get / contains | Notes |
|---|---|---|---|---|
| a list (default choice) | **`ArrayList`** | insertion | `get(i)` O(1), `contains` O(n) | add at end amortised O(1); insert in the middle O(n) (shifts) |
| queue / stack / deque | **`ArrayDeque`** | insertion | ends O(1) | prefer it over `Stack` (legacy, synchronized) and `LinkedList` |
| `LinkedList` | rarely | insertion | `get(i)` **O(n)** | poor cache locality. In practice `ArrayList`/`ArrayDeque` win almost always. |
| unique items, fast lookup | **`HashSet`** | **none** | O(1) | needs good `equals`/`hashCode` (note 01) |
| unique + keep insertion order | `LinkedHashSet` | insertion | O(1) | |
| unique + sorted | `TreeSet` | sorted | O(log n) | uses `compareTo`, not `equals` (§5) |
| key → value (default) | **`HashMap`** | **none** | O(1) avg | one `null` key allowed |
| map keeping insertion order | `LinkedHashMap` | insertion (or access, for LRU) | O(1) | |
| sorted map, range queries | `TreeMap` | sorted by key | O(log n) | `NavigableMap`: `floorEntry`, `headMap`, … |
| shared between threads | `ConcurrentHashMap` | none | O(1) | **no null keys or values** (task 1.7) |

> ⚠️ **`HashMap`/`HashSet` iteration order is unspecified.** It can change between runs or JDK
> versions. If a test or UI depends on order, use `TreeMap` / `LinkedHashMap`, or sort.
> That's why every `groupingBy` in `CatalogQueries` passes `TreeMap::new`.

---

## 2. Immutable, unmodifiable, mutable ⭐⭐⭐

| Created by | Can you add/remove? | `null` elements? | Notes |
|---|---|---|---|
| `List.of(a, b)`, `Set.of`, `Map.of` | ❌ throws `UnsupportedOperationException` | ❌ NPE | `Set.of`/`Map.of` **throw on duplicates** |
| `List.copyOf(x)` | ❌ | ❌ | a snapshot; no copy if `x` is already unmodifiable |
| `stream.toList()` (Java 16) | ❌ | ✅ allowed | ⭐ the default way to finish a stream |
| `collect(Collectors.toList())` | ✅ (currently an `ArrayList`) | ✅ | mutability **not guaranteed** by the spec |
| `collect(Collectors.toUnmodifiableList())` | ❌ | ❌ NPE | |
| `Arrays.asList(array)` | ⚠️ `set` OK, `add`/`remove` throw | ✅ | fixed-size and **backed by the array**: writes go through |
| `Collections.unmodifiableList(x)` | ❌ through the view | ✅ | a **view**: changes to `x` still show through ⚠️ |
| `new ArrayList<>(x)` | ✅ | ✅ | an independent mutable copy |

**Default for return values:** an unmodifiable list (`toList()`, `List.copyOf`). The caller
can't corrupt your data, and the type tells the truth. `CatalogQueriesTest.toListResultIsUnmodifiable`
and `CollectionsTourTest.immutableListsRejectChanges` show the behaviour.

---

## 3. The Map API and HashMap internals ⭐⭐⭐

### 3.1 The methods that replace boilerplate

```java
// ❌ the old dance
Integer old = counts.get(word);
counts.put(word, old == null ? 1 : old + 1);

// ✅ one call
counts.merge(word, 1, Integer::sum);            // absent → 1; present → old + 1
```

| Method | Does | Typical use |
|---|---|---|
| `getOrDefault(k, d)` | value, or `d` if absent | reading counters |
| `putIfAbsent(k, v)` | put only if absent | defaults |
| **`computeIfAbsent(k, k -> new ArrayList<>())`** | create on first use, return it | ⭐ **multimap**: `map.computeIfAbsent(tag, t -> new ArrayList<>()).add(id)` (`CollectionsTour.coursesByTag`) |
| `computeIfPresent(k, (k, v) -> …)` | update only if present | |
| `compute(k, (k, v) -> …)` | update or create (`v` may be null) | |
| **`merge(k, v, fn)`** | absent → `v`; present → `fn(old, v)`; result `null` → remove | ⭐ counting and summing (`wordFrequency`, `revenueByCategoryLoop`) |
| `entrySet()` | iterate keys and values together | `for (var e : map.entrySet()) e.getKey() … e.getValue()` |
| `Map.entry(k, v)` | an immutable pair | tests, `Map.ofEntries` |

### 3.2 `NavigableMap` (`TreeMap`): range questions ⭐⭐

```java
tiers.floorEntry(price)      // greatest key <= price  → "which band is this price in?"
tiers.ceilingKey(x)          // least key >= x
tiers.headMap(x)             // all keys < x
tiers.firstKey() / lastEntry()
```

`CollectionsTour.priceTier` replaces an `if/else` ladder of price bands with **one**
`floorEntry` lookup.

### 3.3 How `HashMap` works (interview favourite) ⭐⭐⭐

1. `hashCode()` of the key is spread (`h ^ (h >>> 16)`) and masked to a **bucket index** in an
   array (default capacity **16**).
2. The bucket holds a **linked list** of entries. `equals()` finds the right one inside it.
3. If a bucket grows to **8** entries (and the table has ≥ 64 buckets), it becomes a
   **red-black tree**, so the worst case is O(log n) instead of O(n).
4. When `size > capacity × load factor (0.75)`, the table **doubles** and every entry is
   re-bucketed (rehashing, O(n), amortised).

**Consequences you must know:**
- A bad `hashCode` (e.g. `return 1;`) puts everything in one bucket, and lookups degrade.
- A key that mutates after insertion is lost (note 01 §4).
- Pre-size a big map to avoid rehashing: `HashMap.newHashMap(expectedSize)` (Java 19+).

### 3.4 `LinkedHashMap` as an LRU cache (classic interview task) ⭐⭐⭐

```java
class LruCache<K, V> extends LinkedHashMap<K, V> {
  private final int capacity;
  LruCache(int capacity) {
    super(16, 0.75f, true);                 // accessOrder = true: get() moves an entry to the end
    this.capacity = capacity;
  }
  @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > capacity;               // evict the least recently used entry
  }
}
```

(In production use a real cache, like Caffeine or Redis in Phase 8, but this answer is expected
in interviews.)

---

## 4. Modifying while iterating ⭐⭐⭐

```java
for (Course c : courses) {
  if (!c.published()) courses.remove(c);    // ❌
}
```

A for-each loop uses an `Iterator`. `ArrayList` counts structural changes (`modCount`), and the
iterator checks that count, so this **usually** throws `ConcurrentModificationException`, even
with a single thread.

**The nasty part (our first test run caught it):** removing the **second-to-last** element
throws nothing. After the removal `hasNext()` sees `cursor == size`, so the loop just ends and
**the last element is never visited**. The test
`butRemovingTheSecondToLastElementSilentlySkipsTheLastOne` proves it. *Fail-fast is
best-effort, not guaranteed.*

**The correct ways:**

```java
courses.removeIf(c -> !c.published());                        // ⭐ simplest (CollectionsTour.removeDrafts)

Iterator<Course> it = courses.iterator();                      // when you need more logic
while (it.hasNext()) { if (!it.next().published()) it.remove(); }

List<Course> live = courses.stream().filter(Course::published).toList();   // or build a new list
```

**Fail-fast vs fail-safe iterators:** `ArrayList` and `HashMap` iterators are **fail-fast**:
they throw CME on concurrent modification. `CopyOnWriteArrayList` and `ConcurrentHashMap`
iterators are **fail-safe / weakly consistent**: they never throw, and work on a snapshot or a
live view.

---

## 5. `Comparable` and `Comparator` ⭐⭐⭐

| | `Comparable<T>` | `Comparator<T>` |
|---|---|---|
| Where | inside the class: `compareTo(T other)` | a separate object or lambda |
| Meaning | the **natural order** (one per type) | any number of orders |
| Example | `Money implements Comparable<Money>` (note 01) | `Comparator.comparingDouble(Course::rating)` |

**Building comparators** (from `CatalogQueries.topRated`):

```java
Comparator.comparingDouble(Course::rating)   // by rating, ascending
    .reversed()                              // → descending
    .thenComparing(Course::title)            // tie-breaker → deterministic order
```

Also: `Comparator.naturalOrder()`, `reverseOrder()`, `nullsFirst(…)`, `nullsLast(…)`, and
`comparing(keyFn, keyComparator)`.

⚠️ **`compareTo`/`compare` must be consistent with `equals` for `TreeSet`/`TreeMap`.** They
treat `compare(a, b) == 0` as **the same element**. A `TreeSet` sorted only by rating
**silently drops** a second course with the same rating. Always add tie-breakers.

⚠️ **Never `return a - b;`** in a comparator. Large values overflow and flip the sign. Use
`Integer.compare`, `Long.compare`, or `Comparator.comparing…`.

---

## 6. Lambdas, functional interfaces, method references ⭐⭐⭐

A **lambda** is an implementation of an interface with exactly **one abstract method** (a
*functional interface*). The ones in `java.util.function`:

| Interface | Shape | Example here |
|---|---|---|
| `Predicate<T>` | `T → boolean` | `Course::published`, `c -> c.ratingCount() >= 100` |
| `Function<T, R>` | `T → R` | `Course::title`, `Function.identity()` |
| `Consumer<T>` | `T → void` | `ids -> ids.sort(...)` |
| `Supplier<T>` | `() → T` | `TreeMap::new` (map factory) |
| `BiFunction<T, U, R>` | `(T, U) → R` | the combiner in `teeing` |
| `BinaryOperator<T>` | `(T, T) → T` | `Money::plus`, `BinaryOperator.maxBy(...)` |
| `UnaryOperator<T>` | `T → T` | `s -> s.strip()` |

**Method references:** four kinds.

| Kind | Example | Same as |
|---|---|---|
| static method | `Integer::sum` | `(a, b) -> Integer.sum(a, b)` |
| instance method of *a* parameter | `Course::title` | `c -> c.title()` |
| instance method of a specific object | `visited::add` | `x -> visited.add(x)` |
| constructor | `TreeMap::new`, `ArrayList::new` | `() -> new TreeMap<>()` |

**Captured variables must be *effectively final*:** a lambda can read a local variable only if
it's never reassigned. That's why `CatalogQueries.revenueByCategory` builds `courseById` once
and only reads it inside the lambda.

---

## 7. How a stream pipeline runs ⭐⭐⭐

```java
courses.stream()                      // 1. SOURCE
    .filter(Course::published)        // 2. INTERMEDIATE ops: lazy, return a new Stream
    .map(Course::title)
    .toList();                        // 3. TERMINAL op: triggers execution, produces a result
```

### Four facts that explain most stream behaviour

1. **Lazy.** Intermediate operations do nothing until a terminal operation runs. A pipeline
   with no terminal op does nothing at all.
2. **Element by element ("vertical"), not stage by stage.** Each element flows through filter →
   map → … before the next one starts.
3. **Short-circuiting.** `findFirst`, `anyMatch`, `limit` stop the flow early. The test
   `streamsAreLazyAndShortCircuit` records which courses the filter actually looked at:
   `[c1, c2, c3, c4]`, stopping at the first premium course. `c5`…`c9` are **never touched**.
4. **One-shot.** A stream can be consumed once. Reusing it throws
   `IllegalStateException: stream has already been operated upon or closed`. Keep the
   **collection**, and create a new stream each time.

**Stateless vs stateful:**
- `filter` and `map` are stateless and stream element by element.
- `sorted` and `distinct` are **stateful**. `sorted` must see *every* element before emitting
  the first, so it buffers the whole stream.

**Streams don't modify their source.** `filter` doesn't remove anything from the list; it
produces a new result.

---

## 8. Stream operations cheat sheet ⭐⭐

| Intermediate (lazy) | Does |
|---|---|
| `filter(pred)` | keep matching elements |
| `map(fn)` | transform each element: 1 → 1 |
| **`flatMap(fn)`** | each element → a stream, all flattened: 1 → 0..n (`allTags`) |
| `mapToInt/Long/Double` | to a primitive stream (§10) |
| `distinct()` | remove duplicates (`equals`) |
| `sorted()` / `sorted(cmp)` | sort (stateful) |
| `limit(n)` / `skip(n)` | first n / drop n |
| `takeWhile` / `dropWhile` | prefix while a condition holds (ordered streams) |
| `peek(fn)` | debugging only: look without changing |
| `gather(gatherer)` | custom intermediate op (Java 24, §12) |

| Terminal | Returns |
|---|---|
| `toList()` | unmodifiable `List` |
| `collect(collector)` | anything: map, set, string, … (§9) |
| `forEach(fn)` | nothing: for **side effects only** (printing, sending) |
| `reduce(identity, op)` | one value folded from all (note 01 §8.4) |
| `count()`, `min(cmp)`, `max(cmp)` | `long`, `Optional<T>` |
| `findFirst()`, `findAny()` | `Optional<T>` (short-circuits) |
| `anyMatch`, `allMatch`, `noneMatch` | `boolean` (short-circuits) |

⭐ **`map` vs `flatMap`:** `courses.stream().map(Course::tags)` gives `Stream<Set<String>>`, a
stream of sets. `flatMap(c -> c.tags().stream())` gives `Stream<String>`: all the tags in one
flat stream.

---

## 9. Collectors ⭐⭐⭐

`collect(...)` takes a **`Collector`**, a recipe for folding elements into a result. These are
the ones you'll use constantly (all in `CatalogQueries`):

### 9.1 `toMap`: watch duplicates and nulls

```java
toMap(Course::id, Function.identity())                       // ⚠️ duplicate key → IllegalStateException
toMap(Course::instructor, identity(),
      BinaryOperator.maxBy(comparing(Course::publishedOn)),  // ⭐ merge fn: resolve duplicates
      TreeMap::new)                                          // map factory
```

- Duplicate keys **throw** unless you give a merge function
  (`toMapThrowsOnDuplicateKeys`, `toMapMergeFunctionKeepsTheNewestCourse`).
- A **`null` value throws NPE**: `toMap` uses `HashMap.merge` internally, which rejects nulls.

### 9.2 `groupingBy`: the workhorse

```java
groupingBy(classifier)                              // Map<K, List<T>>, HashMap (unordered)
groupingBy(classifier, downstream)                  // Map<K, D>
groupingBy(classifier, TreeMap::new, downstream)    // ⭐ choose the map: sorted keys
```

**Downstream collectors** decide what each group becomes:

| Downstream | Group becomes | Used in |
|---|---|---|
| `counting()` | `Long` | `countByCategory`, `tagPopularity` |
| `averagingDouble(fn)` / `summingLong(fn)` | number | `averageRatingByInstructor` |
| `reducing(identity, mapper, op)` | a folded value | `revenueByCategory` (sums `Money` with `Money::plus`) |
| `mapping(fn, downstream)` | transform, then collect | `freeVsPaid` (Course → title) |
| `filtering(pred, downstream)` / `flatMapping` | filter/flatten inside the group | |
| `maxBy(cmp)` / `minBy(cmp)` | `Optional<T>` | `bestCoursePerCategory` |
| `collectingAndThen(c, finisher)` | post-process the result | unwrap the `Optional`, make the list unmodifiable |
| `toSet()`, `toList()` | collections | |

### 9.3 Others

| Collector | Does |
|---|---|
| `partitioningBy(pred, downstream)` | `Map<Boolean, …>`, **always both keys**, even when empty (`partitioningAlwaysHasBothKeys`) |
| `joining(", ", "[", "]")` | concatenate strings with delimiter, prefix and suffix |
| `teeing(c1, c2, combine)` | two collectors in one pass (`priceRange`: min and max together) |
| `summarizingInt(fn)` | count, sum, min, max and average in one pass |

---

## 10. Primitive streams ⭐⭐

`Stream<Integer>` boxes every number into an `Integer` object: allocation, GC, pointer chasing.
`IntStream`, `LongStream` and `DoubleStream` work on raw primitives and add numeric operations:

```java
courses.stream().mapToInt(Course::enrollments).summaryStatistics();   // count/sum/min/max/avg (enrollmentStats)
IntStream.range(0, 10)            // 0..9    (rangeClosed(1, 10) → 1..10)
intStream.sum() / average()       // average() → OptionalDouble (empty stream has no average)
intStream.boxed()                 // back to Stream<Integer> when you need objects
```

---

## 11. Streams vs loops ⭐⭐⭐

`revenueByCategory` (stream) and `revenueByCategoryLoop` (loop + `merge`) give the same answer.
The test `revenueByCategoryStreamAndLoopAgree` checks it. Pick by readability:

| Prefer a **stream** when | Prefer a **loop** when |
|---|---|
| it's a transformation: filter → map → collect | you need to **break/continue** with complex conditions |
| grouping and summarising (collectors shine) | you mutate several things at once |
| the pipeline reads like the requirement | the lambda needs to throw **checked exceptions** (awkward in streams) |
| | index-based logic, or two lists walked together |
| | a very hot path where profiling shows the overhead matters |

**Golden rule: no side effects inside stream operations.** Don't `forEach(list::add)` to build a
result; collect it. Don't modify shared state from `map` or `filter`. (`firstPremiumTitle`
breaks this rule *on purpose*, only so a test can see the evaluation order.)

---

## 12. Parallel streams, gatherers, type witnesses ⭐

**Parallel streams** (`parallelStream()`) split work across the common ForkJoin pool. Rarely
worth it in a web app:
- the pool is **shared** by the whole JVM;
- it only helps with large, CPU-bound, stateless work;
- ordering and merging cost extra;
- shared mutable state breaks it.

Default: **don't**. Measure first.

**Gatherers (Java 24)** let you write custom *intermediate* operations, the way `Collector`
lets you write custom terminal ones. Built-ins live in `Gatherers`:

```java
sales.stream().gather(Gatherers.windowFixed(3)).toList();   // [[s1,s2,s3],[s4,s5,s6],[s7,s8]]  (inBatches)
Gatherers.windowSliding(3)   // [s1,s2,s3], [s2,s3,s4], …    (moving averages)
Gatherers.scan(...)          // running totals
Gatherers.mapConcurrent(n, fn) // map with up to n concurrent (virtual-thread) calls
```

**Type witnesses.** Java infers generic types, but nested generic calls can defeat it. Our
`priceRange` failed to compile with `minBy(Comparator.naturalOrder())` inside `teeing`
("inference variable T has incompatible upper bounds"). The fix is to name the type:
`Comparator.<Money>naturalOrder()`. When you see a confusing inference error, try a type
witness or a typed local variable. Generics properly: task 1.4.

---

## 13. Walkthrough of the code ⭐⭐

| Method / test | Technique |
|---|---|
| `publishedTitles` | the basic pipeline; `toList()` is unmodifiable |
| `topRated` | multi-key `Comparator`, `limit` |
| `countByCategory`, `averageRatingByInstructor` | `groupingBy(…, TreeMap::new, counting() / averagingDouble())` |
| `bestCoursePerCategory` | `maxBy` → `Optional` → `collectingAndThen` |
| `freeVsPaid` | `partitioningBy` + `mapping` |
| `revenueByCategory` vs `…Loop` | an index map for the join, `reducing` with `Money::plus`, vs `Map.merge` |
| `monthlyRevenue` | grouping by a derived key (`YearMonth.from(date)`) |
| `indexById`, `newestCoursePerInstructor` | `toMap` with and without a merge function |
| `allTags`, `tagPopularity` | `flatMap`, `distinct`, `sorted`; counting by identity |
| `titlesIn`, `firstCourseBy`, `anyFree` | `joining`, `findFirst` → `Optional`, `anyMatch` |
| `enrollmentStats`, `priceRange` | `IntSummaryStatistics`, `teeing` |
| `inBatches` | `Gatherers.windowFixed` |
| `firstPremiumTitle` + its test | laziness and short-circuiting, made visible |
| `CollectionsTour` | `merge`, `computeIfAbsent`, `removeIf`, `NavigableMap.floorEntry`, `getFirst`/`getLast` |
| `removingInside…` tests | CME, and the silent second-to-last skip |

---

## 14. Where this goes in Spring / Masternova ⭐⭐⭐

| Where | How |
|---|---|
| **Every controller → DTO** | `courses.stream().map(CourseResponse::from).toList()`: the most common stream you'll write |
| **Dashboards (Phase 5, 11, D5)** | Grouping, averages, revenue per month: **but push aggregation to SQL.** ⭐⭐⭐ `SELECT category, SUM(price_minor) … GROUP BY category` beats loading 100,000 rows into Java to `groupingBy` them. Use streams for what's already in memory (a page of results, a cart, a course's sections). |
| **Indexing for joins in memory** | `toMap(Course::id, identity())` before looping, never a nested search (O(n × m)) |
| **Batch jobs (Phase 4 outbox, Phase 7)** | `windowFixed` for batching; never collect an unbounded result set |
| **JPA** | `findAll()` + `stream().filter()` is a smell: filter in the query (`Specification`, Phase 5) |
| **Responses** | return unmodifiable lists; never expose an entity's internal mutable collection |

---

## 15. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | Why |
|---|---|---|
| `list.remove(x)` inside for-each | `removeIf` / `Iterator.remove` | CME, or a silently skipped element (§4) |
| relying on `HashMap`/`HashSet` order | `TreeMap`/`LinkedHashMap` or sort | the order is unspecified (§1) |
| `toMap` without thinking about duplicates | add a merge function, or rely on it throwing deliberately | `IllegalStateException` in production (§9.1) |
| `toMap` with possibly-null values | filter nulls or use `groupingBy` | NPE (§9.1) |
| `forEach(result::add)` to build a list | `collect` / `toList()` | side effects, not thread-safe |
| reusing a `Stream` variable | stream the collection again | `IllegalStateException` (§7) |
| `findAll().stream().filter(…)` on a big table | filter in the database query | memory and time (§14) |
| `Stream<Integer>` math | `mapToInt(...).sum()` | boxing overhead (§10) |
| `return a - b` in a comparator | `Integer.compare` / `Comparator.comparing` | overflow (§5) |
| `TreeSet` with a comparator that ignores identity | add tie-breakers (`thenComparing`) | "equal" elements are silently dropped (§5) |
| `Arrays.asList(...).add(x)` | `new ArrayList<>(Arrays.asList(...))` | fixed-size list (§2) |
| `LinkedList` "for fast inserts" | `ArrayList` / `ArrayDeque` | O(n) access, poor locality (§1) |
| `parallelStream()` by default | sequential; measure first | shared pool, overhead (§12) |
| `stack = new Stack<>()` | `Deque<T> stack = new ArrayDeque<>()` | legacy synchronized class |

---

## 16. Interview Q&A ⭐⭐⭐

**Q1. How does `HashMap` work? What's its complexity?**
The hash picks a bucket in an array, and `equals` finds the entry in the bucket's list (a tree
once it has 8+ entries). Average O(1), worst O(log n) with treeification. It resizes ×2 when
`size > capacity × 0.75`. One null key is allowed.

**Q2. What happens if two keys have the same `hashCode`?**
A collision: both go in the same bucket, and `equals` tells them apart. Correct, just slower if
it happens a lot.

**Q3. `ArrayList` vs `LinkedList`?**
`ArrayList`: O(1) random access, amortised O(1) append, O(n) middle insert, cache-friendly.
`LinkedList`: O(n) access, O(1) at the ends. Use `ArrayList`, or `ArrayDeque` for queues.

**Q4. `HashMap` vs `LinkedHashMap` vs `TreeMap`?**
Unordered O(1) / insertion- or access-ordered O(1) / sorted O(log n) with range queries.

**Q5. Implement an LRU cache.**
`LinkedHashMap` with `accessOrder = true`, overriding `removeEldestEntry` to return
`size() > capacity` (§3.4).

**Q6. What is `ConcurrentModificationException`, and how do you avoid it?**
A fail-fast iterator detects a structural change made outside the iterator. Use `removeIf`,
`Iterator.remove`, or build a new collection. For concurrency, use concurrent collections.
Bonus: it's best-effort; removing the second-to-last element silently skips the last one.

**Q7. Fail-fast vs fail-safe iterators?**
Fail-fast (`ArrayList`, `HashMap`) throw CME. Fail-safe / weakly consistent
(`CopyOnWriteArrayList`, `ConcurrentHashMap`) iterate a snapshot or a live view and never throw.

**Q8. Intermediate vs terminal operations? What does lazy mean?**
Intermediate ops build the pipeline and run nothing. The terminal op pulls elements through
one at a time, and short-circuiting ops stop early.

**Q9. `map` vs `flatMap`?**
`map` is 1 → 1 (`Stream<Set<String>>`). `flatMap` is 1 → many, flattened (`Stream<String>`).

**Q10. Can you reuse a stream?**
No. It's one-shot; a second terminal op throws `IllegalStateException`.

**Q11. What does `Collectors.toMap` do with duplicate keys? With null values?**
Duplicates throw `IllegalStateException` unless you pass a merge function. Null values throw
NPE.

**Q12. `groupingBy` vs `partitioningBy`?**
Any key vs a boolean key. Partitioning always has both `true` and `false` entries.

**Q13. `Comparable` vs `Comparator`?**
Natural order inside the class vs external, composable orders. Keep both consistent with
`equals` for sorted sets and maps.

**Q14. `stream.toList()` vs `Collectors.toList()`?**
`toList()` is unmodifiable (Java 16+). `Collectors.toList()` has unspecified mutability
(currently an `ArrayList`).

**Q15. When would you NOT use a stream?**
Complex control flow (break/continue), checked exceptions, mutating several things, or a
profiled hot path. And never as a substitute for SQL aggregation over large tables.

---

## 17. Play with it in `jshell` ⭐

```bash
cd patterns/lab && ./mvnw -q compile && jshell --class-path target/classes
```

```java
jshell> import com.masternova.java.streams.*
jshell> var courses = CatalogData.courses()
jshell> CatalogQueries.countByCategory(courses)
jshell> CatalogQueries.revenueByCategory(courses, CatalogData.sales())
jshell> courses.stream().map(Course::tags).toList()                 // map: a list of sets
jshell> courses.stream().flatMap(c -> c.tags().stream()).toList()   // flatMap: a flat list
jshell> var s = courses.stream()
jshell> s.count()
jshell> s.count()                                                    // IllegalStateException
jshell> courses.stream().filter(c -> { System.out.println("filter " + c.id()); return c.isFree(); }).findFirst()
jshell> java.util.stream.IntStream.rangeClosed(1, 5).sum()
jshell> new java.util.HashMap<>(java.util.Map.of("b", 2, "a", 1, "c", 3))   // order? not guaranteed
```

---

## 18. 30-second recall

- **Defaults:**
  - `ArrayList`, `HashMap`, `HashSet`, `ArrayDeque`.
  - Need order? `LinkedHash*`. Need sorting or ranges? `Tree*`.
  - Program to the interface.
- **Immutability:**
  - `List.of`, `copyOf` and `toList()` are unmodifiable (`List.of` rejects nulls).
  - `Arrays.asList` is fixed-size.
  - `unmodifiableList` is only a view.
- **Map API:** `merge` for counting, `computeIfAbsent` for multimaps, `floorEntry` for bands.
- **HashMap:** buckets by hash, `equals` within the bucket, a tree at 8, resize at 0.75.
- **LRU:** `LinkedHashMap(accessOrder)` + `removeEldestEntry`.
- **Iteration:** never remove in for-each; use `removeIf` (CME is best-effort!).
- **Comparators:** `comparing().reversed().thenComparing()`, never `a - b`, keep consistent
  with `equals`.
- **Streams:**
  - Shape: source → lazy intermediates → one terminal.
  - Elements flow one at a time, short-circuiting where possible.
  - One-shot.
  - No side effects.
- **Collectors:**
  - `groupingBy(key, TreeMap::new, downstream)` with `counting`, `averaging`, `reducing`,
    `mapping`, `maxBy`, `collectingAndThen`.
  - `partitioningBy` always has both keys.
  - `toMap` throws on duplicates and nulls.
- **In Spring:** map entities to DTOs with streams, but aggregate big data in SQL.
- **Next:** [04 — Generics](README.md) (task 1.4).
