# 04 — Generics

> **One-liner:** generics let you write a type once (`Page<T>`, `Result<T>`, `Registry<K, V>`)
> and have the **compiler** check every use. Use **bounds** (`T extends Comparable<? super T>`)
> to require abilities, **wildcards** (`? extends` / `? super`, the PECS rule) to accept
> related types, and remember that **type arguments are erased at runtime**.

**Roadmap:** task 1.4 · **Last updated:** 2026-10-02 · **Prev:** [03 — Collections & Streams](03-collections-and-streams.md)
**Code:** [`lab/.../java/generics/`](../lab/src/main/java/com/masternova/java/generics/): `Result`, `Page`, `Keyed` + `Registry`, `Ranking`, `TypedSettings`
**Tests:** [`lab/.../java/generics/`](../lab/src/test/java/com/masternova/java/generics/) · **Run:** `cd patterns/lab && ./mvnw test -Dtest='com.masternova.java.generics.*Test'`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know. In code, `// ⭐` marks the lines to remember.

| # | Section | Priority |
|---|---|---|
| 1 | [Why generics exist](#1-why-generics-exist-) | ⭐⭐⭐ |
| 2 | [Generic classes, records, interfaces](#2-generic-classes-records-interfaces-) | ⭐⭐⭐ |
| 3 | [Generic methods and inference](#3-generic-methods-and-inference-) | ⭐⭐⭐ |
| 4 | [Invariance: `List<Video>` is not a `List<Lecture>`](#4-invariance-listvideo-is-not-a-listlecture-) | ⭐⭐⭐ |
| 5 | [Wildcards and PECS](#5-wildcards-and-pecs-) | ⭐⭐⭐ |
| 6 | [Bounded type parameters](#6-bounded-type-parameters-) | ⭐⭐⭐ |
| 7 | [Type erasure and its consequences](#7-type-erasure-and-its-consequences-) | ⭐⭐⭐ |
| 8 | [Raw types and heap pollution](#8-raw-types-and-heap-pollution-) | ⭐⭐ |
| 9 | [Type tokens: `Class<T>` and super type tokens](#9-type-tokens-classt-and-super-type-tokens-) | ⭐⭐ |
| 10 | [Walkthrough of the code](#10-walkthrough-of-the-code-) | ⭐⭐ |
| 11 | [Generics in Spring](#11-generics-in-spring-) | ⭐⭐⭐ |
| 12 | [Common mistakes](#12-common-mistakes-) | ⭐⭐⭐ |
| 13 | [Interview Q&A](#13-interview-qa-) | ⭐⭐⭐ |
| 14 | [Play with it in `jshell`](#14-play-with-it-in-jshell-) | ⭐ |
| 15 | [30-second recall](#15-30-second-recall) | ⭐⭐⭐ |

---

## 1. Why generics exist ⭐⭐⭐

Before Java 5, collections held `Object`:

```java
List titles = new ArrayList();
titles.add("Java");
titles.add(42);                     // nothing stops this
String t = (String) titles.get(1);  // ClassCastException at RUNTIME, far from the bug
```

With generics, the **element type is part of the type**, the compiler checks every `add`, and
the casts disappear:

```java
List<String> titles = new ArrayList<>();
titles.add(42);                     // ❌ compile error, caught immediately
String t = titles.get(0);           // no cast
```

**Goal:** move type errors from runtime (in production) to compile time (on your screen), and
write reusable code (`Page<T>` serves courses, reviews, orders…) without giving up type safety.

> **Coming from TypeScript:** the syntax looks the same (`Page<T>`, `<T extends X>`), but there
> are big differences:
> - Java generics are **invariant** by default (§4). TS arrays are covariant and unchecked.
> - Java has **wildcards** (`?`). TS has no direct equivalent.
> - Java generics are **erased** at runtime (§7). TS types are erased entirely, so that part
>   will feel familiar.
> - Java type arguments must be **reference types**: `List<int>` is an error. Use `List<Integer>`.

---

## 2. Generic classes, records, interfaces ⭐⭐⭐

```java
public record Page<T>(List<T> items, String nextCursor) { … }        // a generic record
public sealed interface Result<T> {                                  // a generic sealed interface
  record Ok<T>(T value) implements Result<T> {}                      // subtypes pass <T> up
  record Err<T>(String code, String message) implements Result<T> {}
}
public final class Registry<K, V extends Keyed<K>> { … }             // two parameters, one bounded
```

- `T`, `K`, `V`, `R`, `E` are **type parameters**, placeholders filled in per use:
  `Page<CourseSummary>`, `Result<Money>`.
- Naming convention: `T` type, `E` element, `K`/`V` key/value, `R` return, `N` number.
- **Diamond `<>`:** `new ArrayList<>()`, `new Ok<>(value)`. The compiler fills in the type
  argument from context.
- A type parameter can be used for fields, parameters and return types throughout the class.
  It **can't** be used in `static` members: statics belong to the class, not to a particular
  `Page<String>`. Static methods declare their own parameter, as in `Page.empty()` (§3).

---

## 3. Generic methods and inference ⭐⭐⭐

A method can declare its **own** type parameters, written **before the return type**:

```java
static <T> Result<T> ok(T value)                                   // Result.ok(price) → Result<Money>
public static <T> Page<T> empty()                                  // inferred from the TARGET: Page<Course> p = Page.empty();
public <R> Page<R> map(Function<? super T, ? extends R> fn)        // instance method: T from the class, R from the call
```

**Inference** usually works out the type arguments from the arguments and the assignment
target. When it can't, give a **type witness**:

```java
Result.<String>ok("NOPE").flatMap(...)          // ResultTest: no target type to infer from
Comparator.<Money>naturalOrder()                // note 03 §12: nested inside teeing(...)
List.<Gateway>of()                              // RegistryTest: an empty list of a specific type
```

---

## 4. Invariance: `List<Video>` is not a `List<Lecture>` ⭐⭐⭐

`VideoContent` **is a** `LectureContent`. Surely a `List<VideoContent>` is a
`List<LectureContent>`? **No.** If it were, this would compile:

```java
List<VideoContent> videos = new ArrayList<>();
List<LectureContent> lectures = videos;          // suppose this were allowed…
lectures.add(new ArticleContent("Notes", 500));  // …it's a LectureContent, so add is fine…
VideoContent v = videos.get(0);                  // 💥 an Article in a list of Videos
```

So generic types are **invariant**: `List<A>` and `List<B>` are unrelated even when `A extends
B`. The real compiler message:

```text
List<Object> objs = new ArrayList<String>();
→ error: incompatible types: ArrayList<String> cannot be converted to List<Object>
```

**Arrays made the opposite choice, and pay at runtime.** Arrays are *covariant*, so the same
mistake compiles and fails later (`RankingTest.arraysAreCovariantAndFailOnlyAtRuntime`):

```java
Object[] objects = new String[1];   // compiles
objects[0] = 42;                    // ArrayStoreException at runtime
```

Invariance is safe, but too strict when you only *read*. Wildcards loosen it safely.

---

## 5. Wildcards and PECS ⭐⭐⭐

| Wildcard | Accepts | You can | You can't |
|---|---|---|---|
| `List<? extends LectureContent>` | `List<LectureContent>`, `List<VideoContent>`, `List<QuizContent>`, … | **read** elements as `LectureContent` | add anything (except `null`): which subtype is it? |
| `List<? super VideoContent>` | `List<VideoContent>`, `List<LectureContent>`, `List<Object>` | **add** `VideoContent`s | read them back as anything but `Object` |
| `List<?>` | any list | read as `Object`, `size()`, `clear()` | add |

The compiler enforces it:

```text
void f(List<? extends Number> nums) { nums.add(1); }
→ error: incompatible types: int cannot be converted to CAP#1
   (CAP#1 = "some unknown subtype of Number": the compiler can't know 1 fits)
```

### PECS: **P**roducer **E**xtends, **C**onsumer **S**uper ⭐⭐⭐

Ask what the parameter does with T's:

- It **produces** T's for you (you read from it): use **`? extends T`**.
- It **consumes** T's (you put into it): use **`? super T`**.
- It does both: use exactly **`T`**.

```java
// Ranking.copyAll: `from` produces, `to` consumes
public static <T> void copyAll(Collection<? extends T> from, Collection<? super T> to)

copyAll(videos, lectures);     // T = VideoContent → List<LectureContent> is a ? super VideoContent ✅
copyAll(videos, everything);   // List<Object> works too ✅
```

The same rule makes functional parameters flexible. `Result.map`, like `Stream.map`, is:

```java
<R> Result<R> map(Function<? super T, ? extends R> fn)
//                         │           └── fn PRODUCES an R: a subtype of R is fine
//                         └── fn CONSUMES a T: a function accepting any supertype of T is fine
```

`ResultTest.wildcardsLetBroaderFunctionsFit` passes a `Function<Object, Integer>` to a
`Result<Money>` and gets a `Result<Number>`. Without wildcards that's a compile error.

**Rule of thumb for APIs:**
- Put wildcards on **parameters** (they make methods accept more).
- **Never** put them on return types: callers would have to deal with `?`.

---

## 6. Bounded type parameters ⭐⭐⭐

A **bound** restricts what `T` may be, and unlocks that type's methods inside the code:

```java
<T extends Comparable<? super T>> T max(Collection<? extends T> items)   // Ranking.max
class Registry<K, V extends Keyed<K>>                                     // V must have key()
<T extends Number & Comparable<T>>                                        // several bounds: one class, then interfaces
```

Inside `max`, `candidate.compareTo(best)` compiles **only** because `T extends Comparable<…>`.
In `Registry`, `value.key()` compiles only because `V extends Keyed<K>`.
`Registry<String, Integer>` is rejected: `Integer` isn't `Keyed`.

### Why `Comparable<? super T>` and not `Comparable<T>`? ⭐⭐⭐

`LocalDate` implements **`Comparable<ChronoLocalDate>`**, comparable to its *supertype*, not to
itself. With the naive bound:

```java
static <T extends Comparable<T>> T max(List<T> xs)
List<LocalDate> dates = …;
max(dates);
→ error: inference variable T has incompatible equality constraints ChronoLocalDate,LocalDate
```

With `Comparable<? super T>`, "comparable to LocalDate *or any supertype*" accepts it
(`RankingTest.superBoundIsWhatMakesLocalDateWork`). That's exactly why the JDK declares:

```java
public static <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll)
```

(The `Object &` part is a binary-compatibility trick from Java 5. Ignore it.)

---

## 7. Type erasure and its consequences ⭐⭐⭐

Generics are a **compile-time** feature. After checking, the compiler **erases** type arguments:

- `List<String>` becomes `List`.
- `T` becomes its bound (`Object`, or `Comparable` for `T extends Comparable`).
- Casts are inserted where values come out.

At runtime **`List<String>` and `List<Integer>` are the same class**
(`ErasureTest.typeArgumentsDoNotExistAtRuntime`).

**Why?** Backward compatibility. Java 5 code had to run with pre-generics libraries on the same
JVM.

**What erasure forbids** (real javac messages):

| You can't | Because | Error |
|---|---|---|
| `new T()` | no `T` at runtime to instantiate | `unexpected type — required: class, found: type parameter T` |
| `o instanceof List<String>` | the runtime only knows `List` | `Object cannot be safely cast to List<String>` |
| `new T[10]`, `new List<String>[10]` | arrays must know their element type at runtime | `generic array creation` |
| `List<int>` | type arguments must be objects | `unexpected type` |
| overload `f(List<String>)` and `f(List<Integer>)` | both erase to `f(List)` | name clash |
| `static T field` | statics are shared by all parameterisations | |

**The workarounds** are a `Supplier<T>` (pass `ArrayList::new`), a `Class<T>` token (§9), or
`List<?>` for unknown types.

---

## 8. Raw types and heap pollution ⭐⭐

A **raw type** is a generic type used without type arguments (`List` instead of `List<String>`).
It switches generic checking off, and the compiler only warns:

```java
List<String> titles = new ArrayList<>();
List raw = titles;           // ⚠️ raw
raw.add(42);                 // compiles, runs: "heap pollution"
String first = titles.get(0);  // 💥 ClassCastException HERE, far from the real bug
```

`ErasureTest.rawTypesDeferTheErrorToAFarAwayLine` reproduces this. The cast that fails is the
hidden one the compiler inserted at `get`.

**Rules:**
- Never write raw types in new code.
- `List<?>` is the type-safe "list of something".
- Treat every `unchecked` warning as a bug report until you've proven otherwise, and suppress
  it on the smallest possible scope with a comment explaining why.

---

## 9. Type tokens: `Class<T>` and super type tokens ⭐⭐

Because `T` is erased, code that needs the type at runtime takes it as an argument. That's a
**type token**:

```java
public <T> T get(Class<T> type) { return type.cast(values.get(type)); }   // TypedSettings

settings.put(Integer.class, 30);
Integer retries = settings.get(Integer.class);     // typed, no cast at the call site
```

`Class.cast` is a **checked** cast, so even a raw-type trick can't sneak a `String` in as an
`Integer` (`typeTokenRejectsAWrongValueEvenThroughARawCall`). Spring uses this everywhere:
`context.getBean(PaymentGateway.class)`, `restClient.get().retrieve().body(Course.class)`.

**But `Class<T>` can't express `List<Course>`** (`List<Course>.class` doesn't exist). The fix
is a **super type token**: an anonymous subclass *does* keep its generic superclass in the
class file:

```java
new ParameterizedTypeReference<List<CourseResponse>>() {}   // Spring: note the {}
new TypeReference<List<CourseResponse>>() {}                // Jackson
```

You'll use these in Phase 9 when a `RestClient` reads a list from Razorpay.

---

## 10. Walkthrough of the code ⭐⭐

| File | Shows |
|---|---|
| [`Result<T>`](../lab/src/main/java/com/masternova/java/generics/Result.java) | A generic sealed interface; generic static factories; `map`/`flatMap` with PECS signatures; generic record patterns `case Ok<T>(T value)`; `narrow` rebuilds a `Result<? extends R>` as a `Result<R>` with no unchecked cast |
| [`Page<T>`](../lab/src/main/java/com/masternova/java/generics/Page.java) | A generic record; `empty()` inferred from the target; `fromRows`: the **limit + 1 keyset trick** behind our API's cursor pagination; `map` for entity → DTO |
| [`Keyed<K>`](../lab/src/main/java/com/masternova/java/generics/Keyed.java) + [`Registry<K, V extends Keyed<K>>`](../lab/src/main/java/com/masternova/java/generics/Registry.java) | A bounded class parameter; `Collection<? extends V>`; the Strategy registry (pattern note 01) written once for any type. **And** why not `Map.copyOf` (order!). |
| [`Ranking`](../lab/src/main/java/com/masternova/java/generics/Ranking.java) | `max` with `<T extends Comparable<? super T>>`; `copyAll` (PECS); `totalMinutes(Collection<? extends LectureContent>)` |
| [`TypedSettings`](../lab/src/main/java/com/masternova/java/generics/TypedSettings.java) | A type-safe heterogeneous container with `Class<T>` tokens |
| `ErasureTest`, `RankingTest` | erasure, raw types, array covariance: the runtime evidence for §4, §7, §8 |

> 🐛 **A bug we hit while writing this:** `Registry` first stored its map with `Map.copyOf`,
> and `keys()` came back as `[STRIPE, RAZORPAY]`. `Map.copyOf`'s iteration order is
> unspecified and varies between runs (note 03 §1). The fix is an unmodifiable view over a
> private `LinkedHashMap`. The comment in the code explains it.

---

## 11. Generics in Spring ⭐⭐⭐

You'll read these signatures every day. Now you can decode them:

| Spring type | Meaning |
|---|---|
| `interface CourseRepository extends JpaRepository<Course, UUID>` | the entity type and its id type. Spring Data generates `findById(UUID)` returning `Optional<Course>`. |
| `ResponseEntity<CourseResponse>` | an HTTP response whose body is a `CourseResponse` |
| `org.springframework.data.domain.Page<T>` / `Slice<T>` | Spring Data's own pages (OFFSET-based; we'll use cursor `Page<T>` like ours) |
| `ApplicationListener<OrderPaid>`, `@EventListener void on(OrderPaid e)` | listeners are matched by the event's generic type |
| `Converter<String, CourseId>` | type conversion for request parameters |
| `List<PaymentGateway>` constructor parameter | Spring injects **every bean** of that type, and `Registry.of(gateways)` indexes them |
| `ParameterizedTypeReference<List<T>>` | the super type token from §9 |
| `<T> T getBean(Class<T> type)` | the type token from §9 |

---

## 12. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | Why |
|---|---|---|
| raw `List`, `Map`, `Class` | `List<String>`, `List<?>`, `Class<?>` | heap pollution, a CCE far from the bug (§8) |
| parameter `List<LectureContent>` when you only read | `List<? extends LectureContent>` | invariance rejects `List<VideoContent>` (§4–5) |
| parameter `List<VideoContent>` when you only add | `List<? super VideoContent>` | accepts `List<LectureContent>` / `List<Object>` too |
| a wildcard in a **return** type | a concrete type | callers can't use `?` comfortably |
| `<T extends Comparable<T>>` | `<T extends Comparable<? super T>>` | fails for `LocalDate` & co. (§6) |
| `new T()`, `new T[n]`, `instanceof List<String>` | `Supplier<T>`, `Class<T>`, `List<?>` | erasure (§7) |
| ignoring `unchecked` warnings | fix it, or suppress on the narrowest scope with a reason | they're compile-time `ClassCastException`s waiting to happen |
| `List<int>` | `List<Integer>` / `IntStream` / `int[]` | primitives aren't type arguments |
| `Map.copyOf` when order matters | `LinkedHashMap` + unmodifiable view | unspecified order (§10) |
| `TypeReference<List<X>>()` without `{}` | `new TypeReference<List<X>>() {}` | the anonymous subclass is what keeps the type |

---

## 13. Interview Q&A ⭐⭐⭐

**Q1. Why generics?**
Compile-time type safety and no casts, with reusable code. Errors move from runtime to compile
time.

**Q2. What is type erasure? Why does Java use it?**
Type arguments exist only at compile time. The compiler checks them, then erases them to their
bounds and inserts casts. It was done for backward compatibility with pre-Java-5 code.
Consequences: no `new T()`, no `instanceof List<String>`, no generic arrays, no overloading by
type argument.

**Q3. Is `List<String>` a subtype of `List<Object>`?**
No. Generics are invariant, because otherwise you could add an `Integer` to a list of strings
through the `List<Object>` reference. Arrays are covariant, and fail at runtime with
`ArrayStoreException`.

**Q4. Explain `? extends` vs `? super`. What is PECS?**
`? extends T` is a producer: you can read `T`s and can't add. `? super T` is a consumer: you can
add `T`s and read only `Object`. Producer Extends, Consumer Super. Example:
`copy(List<? extends T> src, List<? super T> dst)`.

**Q5. What does `<T extends Comparable<? super T>>` mean?**
T must be comparable to itself or to a supertype. It's needed for types like `LocalDate` that
implement `Comparable<ChronoLocalDate>`.

**Q6. `List<?>` vs `List<Object>` vs raw `List`?**
`List<?>` is a list of some unknown type: read-only (as `Object`) and type-safe. `List<Object>`
is specifically a list of `Object`, and only accepts `List<Object>`. Raw `List` turns checking
off entirely, which is unsafe.

**Q7. Why can't you create `new T()` or `new T[]`?**
`T` is erased, so the JVM doesn't know which class to instantiate or what array type to
create. Pass a `Supplier<T>` or a `Class<T>`.

**Q8. What is heap pollution?**
A variable of a parameterised type that refers to an object of the wrong type, usually via raw
types or unchecked casts. It shows up as a `ClassCastException` at some later, innocent-looking
line.

**Q9. How do you get a generic type at runtime?**
For a single class, use a `Class<T>` token. For a parameterised type like `List<Course>`, use a
super type token (`new ParameterizedTypeReference<List<Course>>() {}`), which works because an
anonymous subclass records its generic superclass.

**Q10. Can a static method use the class's type parameter?**
No. Statics aren't tied to a parameterisation. A static method declares its own:
`static <T> Page<T> empty()`.

---

## 14. Play with it in `jshell` ⭐

```bash
cd patterns/lab && ./mvnw -q compile && jshell --class-path target/classes
```

```java
jshell> import com.masternova.java.generics.*
jshell> Page<String> p = Page.fromRows(java.util.List.of("a","b","c","d"), 3, s -> "after:" + s)
jshell> p.map(String::toUpperCase)
jshell> Result.ok(5).map(x -> x * 2)
jshell> Result.<Integer>err("X", "boom").map(x -> x * 2)
jshell> new java.util.ArrayList<String>().getClass() == new java.util.ArrayList<Integer>().getClass()
jshell> java.util.List<Object> objs = new java.util.ArrayList<String>()      // compile error: invariance
jshell> Object[] arr = new String[1]; arr[0] = 1                             // ArrayStoreException
jshell> Ranking.max(java.util.List.of(java.time.LocalDate.now(), java.time.LocalDate.MIN))
```

---

## 15. 30-second recall

- **Purpose:** compile-time type safety and reuse. `Page<T>`, `Result<T>`, `Registry<K, V>`.
- **Generic methods:** `<T>` goes before the return type; the type is inferred. When inference
  fails, add a type witness (`Comparator.<Money>naturalOrder()`).
- **Invariance:** `List<Video>` is not a `List<Lecture>`. Arrays are covariant, so that
  mistake becomes a runtime `ArrayStoreException`.
- **PECS:**
  - `? extends T`: read only (producer).
  - `? super T`: add only (consumer).
  - Put wildcards on parameters, never on return types.
  - `Function<? super T, ? extends R>`.
- **Bounds:** `T extends X` unlocks X's methods. Use `Comparable<? super T>`, because of
  `LocalDate`.
- **Erasure:**
  - Type arguments are gone at runtime.
  - So: no `new T()`, no `new T[]`, no `instanceof List<String>`, no `List<int>`.
  - Use `Class<T>` tokens and super type tokens.
- **Raw types:** they disable checking, so the `ClassCastException` lands at a later read.
  Never use them.
- **Next:** [05 — Exceptions & `Optional`](05-exceptions-and-optional.md) (task 1.5).
