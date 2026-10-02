# 01 — Records & Value Objects

> **One-liner:** a *value object* is a small, immutable type defined only by its value
> (`Money`, `LectureDuration`). A Java `record` gives you one in a single line, and the
> **compact constructor** guarantees an invalid one can never exist.

**Roadmap:** task 1.1 · **Last updated:** 2026-10-02
**Code:** [`Money.java`](../lab/src/main/java/com/masternova/java/valueobject/Money.java) · [`LectureDuration.java`](../lab/src/main/java/com/masternova/java/valueobject/LectureDuration.java)
**Tests:** [`MoneyTest.java`](../lab/src/test/java/com/masternova/java/valueobject/MoneyTest.java) · [`LectureDurationTest.java`](../lab/src/test/java/com/masternova/java/valueobject/LectureDurationTest.java)
**Run:** `cd patterns/lab && ./mvnw test -Dtest='MoneyTest,LectureDurationTest'`

---

## How to read this

| Mark | Means | What to do |
|---|---|---|
| ⭐⭐⭐ | **Must know.** Interviewers ask it, and bugs come from not knowing it. | Read twice. Be able to explain it without notes. |
| ⭐⭐ | **Use daily.** You'll write this in every Spring module. | Understand it and recognise it in code. |
| ⭐ | **Good to know.** Depth and edge cases. | Skim now, come back later. |

In the Java files, comments starting with **`// ⭐`** mark the lines worth remembering.

| # | Section | Priority |
|---|---|---|
| 1 | [Value object vs entity](#1-value-object-vs-entity-) | ⭐⭐⭐ |
| 2 | [`==` vs `equals` (coming from TypeScript)](#2--vs-equals-coming-from-typescript-) | ⭐⭐⭐ |
| 3 | [Records in depth](#3-records-in-depth-) | ⭐⭐⭐ |
| 4 | [The `equals` / `hashCode` contract](#4-the-equals--hashcode-contract-) | ⭐⭐⭐ |
| 5 | [Immutability](#5-immutability-) | ⭐⭐⭐ |
| 6 | [Money: the hard parts](#6-money-the-hard-parts-) | ⭐⭐⭐ |
| 7 | [Walkthrough: `Money.java`](#7-walkthrough-moneyjava-) | ⭐⭐ |
| 8 | [Walkthrough: `LectureDuration.java`](#8-walkthrough-lecturedurationjava-) | ⭐⭐ |
| 9 | [Exceptions used here](#9-exceptions-used-here-) | ⭐⭐ |
| 10 | [Reading the tests (JUnit 5 + AssertJ)](#10-reading-the-tests-junit-5--assertj-) | ⭐⭐ |
| 11 | [Where this goes in Spring / Masternova](#11-where-this-goes-in-spring--masternova-) | ⭐⭐ |
| 12 | [Common mistakes](#12-common-mistakes-) | ⭐⭐⭐ |
| 13 | [Interview Q&A](#13-interview-qa-) | ⭐⭐⭐ |
| 14 | [Play with it in `jshell`](#14-play-with-it-in-jshell-) | ⭐ |
| 15 | [30-second recall](#15-30-second-recall) | ⭐⭐⭐ |

---

## 1. Value object vs entity ⭐⭐⭐

Every domain model has two kinds of objects:

| | **Entity** | **Value object** |
|---|---|---|
| Identity | Has an **id**. Two users named "Asha" are two different people. | **No identity.** Two ₹100 notes are *the same* ₹100. |
| Equality | By id | By **all fields** |
| Changes over time? | Yes: a user edits their name, an order gets paid | **Never.** "Changing" one means creating a new one. |
| Lifecycle | Created, updated, deleted, stored in its own table | Lives inside an entity (a column or embedded fields) |
| In Masternova | `User`, `Course`, `Order`, `Enrollment` (JPA `@Entity`) | `Money`, `LectureDuration`, `Email`, `Slug`, `CourseId` |
| In Java | a regular class (JPA needs mutability) | a **`record`** |

**Why bother? Primitive obsession.** Without value objects, a course price is a bare `long`:

```java
long price = 149900;          // paise? rupees? which currency?
long duration = 245;          // seconds
long total = price + duration; // compiles. Nonsense.
price = -5;                    // compiles. Nonsense.
```

With value objects, the **compiler** rejects `price.plus(duration)` (wrong type), and the
**constructor** rejects `new Money(-5, INR)`. Whole bug categories disappear.

> 💡 **Rule of thumb.** If a primitive has rules (must be positive, must have a currency,
> must match a format) or units (seconds, paise), wrap it in a value object.

---

## 2. `==` vs `equals` (coming from TypeScript) ⭐⭐⭐

```ts
// TypeScript / JavaScript
const a = { amountMinor: 100, currency: 'INR' };
const b = { amountMinor: 100, currency: 'INR' };
a === b;            // false: compares references; JS has no built-in value equality
```

```java
// Java
Money a = Money.of(100, "INR");
Money b = Money.of(100, "INR");
a == b;             // false: == compares REFERENCES (same object in memory?), like ===
a.equals(b);        // true:  equals() compares VALUES (the record generated it)
```

| Use `==` for | Use `.equals()` for |
|---|---|
| primitives: `int`, `long`, `boolean`, `char`, `double` | **every object**: `String`, records, `BigDecimal`, `List`, … |
| enums (one instance per constant) | |
| a deliberate "is this the very same object?" check | |

⚠️ **Classic bug:** `if (status == "PAID")` compares references. It might *sometimes* work
because of string interning, which makes the bug worse. Always write `"PAID".equals(status)`.
Putting the literal first is also null-safe.

---

## 3. Records in depth ⭐⭐⭐

### 3.1 What one line gives you

```java
public record Money(long amountMinor, Currency currency) implements Comparable<Money> { ... }
//                  └──────────── components ─────────┘
```

Before records (Java 16), the same value object took **all of this**. Read it once, so you
know what the compiler now writes for you:

```java
public final class Money {                       // final: nobody can subclass and break equality
  private final long amountMinor;                // private final: set once, never changed
  private final Currency currency;

  public Money(long amountMinor, Currency currency) {   // canonical constructor
    this.amountMinor = amountMinor;
    this.currency = currency;
  }

  public long amountMinor() { return amountMinor; }     // accessors: no "get" prefix in records
  public Currency currency() { return currency; }

  @Override public boolean equals(Object o) {            // value equality over ALL fields
    if (this == o) return true;
    if (!(o instanceof Money other)) return false;
    return amountMinor == other.amountMinor && currency.equals(other.currency);
  }

  @Override public int hashCode() { return Objects.hash(amountMinor, currency); }

  @Override public String toString() {
    return "Money[amountMinor=" + amountMinor + ", currency=" + currency + "]";
  }
}
```

So a record is automatically:

- `final`
- holding `private final` fields
- given a canonical constructor and accessors
- given `equals`, `hashCode` and `toString`

### 3.2 The compact constructor: where the rules live ⭐⭐⭐

```java
public record Money(long amountMinor, Currency currency) {
  public Money {                                   // ← no "(long amountMinor, Currency currency)"
    Objects.requireNonNull(currency, "currency");
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amount cannot be negative: " + amountMinor);
    }
    // fields are assigned AFTER this block, automatically
  }
}
```

- It's the canonical constructor without the boilerplate. You only write **validation** or
  **normalisation**.
- Every creation path goes through it: `new Money(...)`, `Money.of(...)`, `Money.parse(...)`,
  `plus`/`minus` (they call `new`), and Jackson deserialization. So **no invalid Money can
  exist anywhere in the program.**
- That is the big payoff. `minus` never checks for a negative result itself:
  `new Money(a - b, currency)` throws if it would be negative. **The invariant does the work.**
- You can also normalise by reassigning the parameter:

  ```java
  public record Email(String value) {
    public Email {
      value = value.strip().toLowerCase(Locale.ROOT);   // reassign the PARAMETER, not this.value
      if (!value.contains("@")) throw new IllegalArgumentException("not an email: " + value);
    }
  }
  ```

> Writing the full canonical constructor (`public Money(long amountMinor, Currency currency) { this.amountMinor = ...; }`) is also legal. Prefer the compact form; it's shorter and you can't forget to assign a field.

### 3.3 Static factory methods ⭐⭐

```java
Money.of(149_900, "INR")          // reads like the domain
Money.zero("INR")
Money.parse("1499.00", "INR")
LectureDuration.ofMinutes(90)
```

Compare `new Money(149_900, Currency.getInstance("INR"))`. Factories:

1. have **names** that say what they do (`zero`, `parse`, `ofMinutes`);
2. can **convert input** (a code to a `Currency`, text to a number);
3. can **return a cached instance** (`LectureDuration.ZERO`).

This is *Effective Java*, item 1. The JDK does it everywhere: `List.of`, `Duration.ofSeconds`,
`Currency.getInstance`.

> 💡 `149_900`: underscores in number literals are allowed anywhere between digits. Use them
> for readability.

### 3.4 What records can and can't do ⭐⭐

| ✅ Can | ❌ Can't |
|---|---|
| implement interfaces (`Comparable<Money>`) | **extend** a class (they implicitly extend `java.lang.Record`) |
| have static fields (`ZERO`) and static methods (factories) | declare extra **instance** fields |
| have instance methods (`plus`, `format`) | be subclassed (they're `final`) |
| override an accessor or `toString` | have setters or mutable state |
| be nested or local (declared inside a method) | be a JPA `@Entity` (see §11) |
| be generic: `record Pair<A, B>(A first, B second) {}` | |

### 3.5 When to use a record ⭐⭐

| Use a record for | Use a class for |
|---|---|
| value objects (`Money`) | JPA entities (they need mutability and a no-arg constructor) |
| DTOs: request/response bodies (`web/dto/`) | Spring services and controllers (they hold dependencies and behaviour) |
| events (`OrderPaid`), command objects | anything with identity or a lifecycle |
| multiple return values, map keys, tuples | |

---

## 4. The `equals` / `hashCode` contract ⭐⭐⭐

`HashMap` and `HashSet` find things in two steps:
1. `hashCode()` picks a bucket.
2. `equals()` searches inside that bucket.

So the two methods must agree:

1. **Equal objects must have equal hash codes.** If `a.equals(b)` then
   `a.hashCode() == b.hashCode()`. The reverse isn't required: equal hashes can just be a
   collision.
2. **`equals` is reflexive, symmetric and transitive.** `a.equals(b)` ⇔ `b.equals(a)`.
3. **They must not change while the object sits in a hash collection.**

Break rule 1 (override `equals`, forget `hashCode`) and a `HashSet` happily holds two "equal"
objects. Break rule 3 and entries get **lost**:

```java
class MutableKey { int id; /* equals + hashCode over id */ }

Map<MutableKey, String> map = new HashMap<>();
MutableKey k = new MutableKey(); k.id = 1;
map.put(k, "course");
k.id = 2;                       // hashCode changes → the entry is in the WRONG bucket now
map.get(k);                     // null 😱  (and the entry still sits in the map: a leak)
```

**Records satisfy all three rules automatically**, provided every component is itself immutable.
`Money`'s components are a primitive `long` and an immutable `Currency`, so its generated
`equals`/`hashCode` are correct. That's why `MoneyTest.ValueSemantics` passes without a single
line of equality code.

---

## 5. Immutability ⭐⭐⭐

An immutable object never changes after construction. Every "change" returns a new object:

```java
Money price = Money.of(1_000, "INR");
Money total = price.plus(Money.of(500, "INR"));
// price is STILL 1000. total is a new object with 1500.
```

Java's own `String`, `BigDecimal`, `LocalDate` and `Duration` work this way. A common
beginner bug:

```java
BigDecimal sum = BigDecimal.ZERO;
sum.add(BigDecimal.TEN);         // ❌ result thrown away; sum is still 0
sum = sum.add(BigDecimal.TEN);   // ✅
```

**Why immutability matters:**

| Benefit | Because |
|---|---|
| **Thread-safe for free** | No thread can change it, so no locks are needed. Share one `Money` across a thousand virtual threads. |
| **Safe to share and cache** | `LectureDuration.ZERO` is one shared instance; nobody can corrupt it |
| **Safe as a map key** | Its hash can never change (§4, rule 3) |
| **Easy to reason about** | A value you were handed can't change under you |

### The shallow-immutability trap ⭐⭐⭐

Record fields are `final`, but `final` only freezes the **reference**, not the object it
points to:

```java
record Cart(List<Item> items) {}

var list = new ArrayList<Item>();
var cart = new Cart(list);
list.add(new Item(...));          // 😱 the "immutable" cart just changed
cart.items().clear();             // 😱 and anyone can empty it
```

The fix is a **defensive copy** with `List.copyOf`, which is unmodifiable:

```java
record Cart(List<Item> items) {
  Cart { items = List.copyOf(items); }   // snapshot in, unmodifiable out (also rejects nulls)
}
```

`Money.allocate()` returns `List.copyOf(shares)` for the same reason, and the test
`theReturnedListCannotBeModified` proves it.

| Copy method | Result |
|---|---|
| `List.copyOf(x)`, `Set.copyOf`, `Map.copyOf` | unmodifiable copy (Java 10+). **Use this.** |
| `List.of(a, b, c)` | unmodifiable, built from elements |
| `Collections.unmodifiableList(x)` | a read-only *view*: changes to `x` still show through ⚠️ |
| `new ArrayList<>(x)` | a mutable copy |

### "Withers": changing one field ⭐

Records have no setters. To "change" one field, add a method that returns a copy:

```java
public Money withAmount(long newAmountMinor) { return new Money(newAmountMinor, currency); }
```

---

## 6. Money: the hard parts ⭐⭐⭐

### 6.1 Never `double`

```java
System.out.println(0.1 + 0.2);       // 0.30000000000000004
System.out.println(1.10 - 1.00);     // 0.10000000000000009
System.out.println(0.1 * 3 == 0.3);  // false
```

`double` is **binary** floating point. 0.1 has no exact binary representation, just as 1/3 has
no exact decimal one. For money that error becomes a missing paisa on an invoice, and
reconciliation reports that never balance.

| Representation | Verdict |
|---|---|
| `double` / `float` | ❌ **never for money** |
| `BigDecimal` | ✅ exact decimal. Ideal for **parsing and display**, verbose for arithmetic. |
| `long` minor units | ✅ exact and fast. ₹1,499.00 = `149900` paise. Stripe and Razorpay use this on the wire. |

**Masternova's choice:** store and calculate in `long` minor units, and use `BigDecimal` only
at the edges (`parse` and `display`).

### 6.2 `BigDecimal` pitfalls ⭐⭐⭐

```java
new BigDecimal(0.1);       // 0.1000000000000000055511151231257827021181583404541015625 😱
new BigDecimal("0.1");     // 0.1 ✅  build from a STRING
BigDecimal.valueOf(0.1);   // 0.1 ✅  (goes via Double.toString; OK for literals)

new BigDecimal("1.0").equals(new BigDecimal("1.00"));     // false 😱  equals also compares SCALE
new BigDecimal("1.0").compareTo(new BigDecimal("1.00"));  // 0 ✅   compareTo compares VALUE
```

So **compare `BigDecimal` values with `compareTo`, not `equals`.** This is a favourite interview
question, and also why `BigDecimal` makes an awkward `HashMap` key.

### 6.3 Overflow: fail loudly ⭐⭐⭐

```java
long x = Long.MAX_VALUE;     // 9,223,372,036,854,775,807
x + 1;                       // -9,223,372,036,854,775,808: silently wraps around 😱
Math.addExact(x, 1);         // throws ArithmeticException ✅
Math.multiplyExact(x, 2);    // throws ArithmeticException ✅
```

Silent wrap-around turns a huge price into a *negative* one, the kind of bug that ends up in
the news. `Money` uses `Math.addExact` and `Math.multiplyExact` everywhere overflow is possible.

### 6.4 Rounding without floating point ⭐⭐

A 15% coupon on ₹9.99 is a discount of 149.85 paise. We must round to a whole paisa.

```java
long discount = (amountMinor * percent + 50) / 100;   // integer "round half-up"
// (999 × 15 + 50) / 100 = (14985 + 50) / 100 = 15035 / 100 = 150   (integer division truncates)
```

Adding half the divisor before integer division rounds half-up for non-negative numbers.

> ⭐ **Banker's rounding.** `RoundingMode.HALF_EVEN` rounds .5 to the nearest *even* digit, so
> rounding errors cancel out over many operations. Accounting systems often require it. Know
> that it exists.

### 6.5 Splitting money: Fowler's allocation ⭐⭐⭐

Split ₹10.00 (1000 paise) between 3 instructors:

```text
naive:   1000 / 3 = 333 each   → 333 × 3 = 999     → 1 paisa vanished
correct: base = 1000 / 3 = 333, remainder = 1000 % 3 = 1
         first `remainder` shares get +1           → [334, 333, 333] = 1000 ✅
```

From Martin Fowler's *Patterns of Enterprise Application Architecture* (the Money pattern).
Interviewers love it because the naive version *looks* right.

---

## 7. Walkthrough: `Money.java` ⭐⭐

Open [`Money.java`](../lab/src/main/java/com/masternova/java/valueobject/Money.java) next to
this.

| Member | What to notice |
|---|---|
| `record Money(long amountMinor, Currency currency)` | Two components, so equality is over both: ₹100 ≠ $100 |
| `implements Comparable<Money>` | Gives a **natural order**, so `sorted()`, `Collections.max` and `TreeMap` work |
| compact constructor | `Objects.requireNonNull` → NPE; negative → IAE. **The single place rules live.** |
| `of(long, String)` | `Currency.getInstance` already throws IAE for `"XYZ"`. Don't wrap what's already right. |
| `parse(String, String)` | `new BigDecimal(text)` (exact) → `movePointRight(fractionDigits)` → `longValueExact()` throws if a fraction remains → rethrown as IAE **with the cause attached** |
| `plus` | `requireSameCurrency` + `Math.addExact` |
| `minus` | No negative check: **the constructor rejects it** |
| `times` | Validates quantity, then `Math.multiplyExact` |
| `percentOff` | Range check, then integer half-up rounding (§6.4) |
| `allocate` | Fowler (§6.5), returns `List.copyOf` (unmodifiable) |
| `compareTo` | `Long.compare(a, b)`. **Never `(int) (a - b)`**: subtraction overflows, and the cast truncates. |
| `display` | `BigDecimal.valueOf(unscaled, scale)` → `"1499.00"`; JPY has 0 digits → `"500"` |
| `requireSameCurrency` | `private` helper: one rule, one place, a clear message |

**Exception chaining in `parse`:**

```java
} catch (ArithmeticException tooPrecise) {
  throw new IllegalArgumentException("more decimals than INR allows: " + major, tooPrecise);
  //                                                                            ^ the cause
}
```

Always pass the original exception as the **cause**. Stack traces then show both, and nobody
has to guess why it failed.

---

## 8. Walkthrough: `LectureDuration.java` ⭐⭐

### 8.1 Domain type vs `java.time.Duration`

`java.time.Duration` allows negatives and nanoseconds, and doesn't know the `"1:02:03"` UI
format. `LectureDuration` states the domain rules once (whole seconds, non-negative, this
format) and offers `toJavaDuration()` as a bridge when an API needs the JDK type.

> 💡 **Naming:** calling it `Duration` would clash with `java.time.Duration`, and every file
> using both would need fully qualified names. Prefer domain names.

### 8.2 Regex: `Pattern` + `Matcher` ⭐⭐

```java
private static final Pattern MINUTES_SECONDS = Pattern.compile("(\\d+):([0-5]\\d)");

Matcher m = MINUTES_SECONDS.matcher("4:05");
if (m.matches()) {                       // the WHOLE string must match
  int minutes = Integer.parseInt(m.group(1));   // "4"
  int seconds = Integer.parseInt(m.group(2));   // "05"
}
```

| Piece | Meaning |
|---|---|
| `\\d` | a digit. In a Java string literal a backslash must be escaped, so the regex `\d` is written `"\\d"`. |
| `+` | one or more |
| `[0-5]\\d` | exactly two digits, 00–59. This is how `"1:60"` and `"4:5"` get rejected. |
| `( … )` | a **capture group**, read with `group(1)`, `group(2)`, … (`group(0)` is the whole match) |
| `matches()` vs `find()` | `matches()` = the whole input; `find()` = anywhere inside it. With `find()`, `" 4:05"` would be accepted. |

⭐ **Compile once.** `Pattern.compile` is expensive, and a `Pattern` is immutable and
thread-safe, so keep it in a `static final` field. A `Matcher` is **not** thread-safe: create
one per call, as `parse` does.

### 8.3 Formatting ⭐⭐

```java
"%d:%02d:%02d".formatted(1, 2, 3)   // "1:02:03"   (Java 15+; same as String.format(template, args))
```

`%d` is an integer. `%02d` is an integer padded to at least 2 digits with zeros. Integer
arithmetic does the splitting: `seconds / 3600` is the hours, `seconds % 3600 / 60` the
minutes, `seconds % 60` the seconds.

### 8.4 Your first stream: `reduce` ⭐⭐

```java
return durations.stream().reduce(ZERO, LectureDuration::plus);
```

- `stream()` turns the list into a pipeline of elements.
- `reduce(identity, accumulator)` folds all elements into one value: `ZERO.plus(d1).plus(d2)…`
- **The identity matters.** For an empty list the result is `ZERO`. The one-argument form,
  `reduce(LectureDuration::plus)`, returns an `Optional<LectureDuration>` instead, because an
  empty list has no answer.
- `LectureDuration::plus` is a **method reference**, short for `(a, b) -> a.plus(b)`.

Task 1.3 covers streams properly. This is the first taste.

### 8.5 Static constants on a record ⭐

```java
public static final LectureDuration ZERO = new LectureDuration(0);
```

Records can't have extra *instance* fields, but *static* fields are fine. Sharing one `ZERO`
is safe because it's immutable (§5).

---

## 9. Exceptions used here ⭐⭐

| Situation | Exception | Why this one |
|---|---|---|
| a required argument is `null` | `NullPointerException` via `Objects.requireNonNull(x, "name")` | JDK convention, and the message names the argument |
| an argument breaks a rule (negative, wrong currency, bad text) | `IllegalArgumentException` | "you passed something invalid" |
| arithmetic overflow | `ArithmeticException` | what `Math.*Exact` throws; let it propagate |
| malformed number text | `NumberFormatException` | **a subclass of IAE**, so `parse("abc")` already throws the right type |

All of these are **unchecked**: they extend `RuntimeException`, so callers aren't forced to
`catch` them. A broken invariant is a *programming error*, not a recoverable condition.
Checked vs unchecked exceptions are covered in task 1.5.

---

## 10. Reading the tests (JUnit 5 + AssertJ) ⭐⭐

The tests are the **executable specification**. Read the test names top to bottom, and you
have the rules.

```java
@Nested                       // groups related tests; shows as MoneyTest$Creation in reports
class Creation {
  @Test
  void rejectsANegativeAmount() {
    assertThatThrownBy(() -> new Money(-1, INR))           // AssertJ: run the lambda...
        .isInstanceOf(IllegalArgumentException.class);    // ...expect this exception type
  }
}

@ParameterizedTest(name = "rejects \"{0}\"")               // one test, many inputs
@ValueSource(strings = {"", "abc", "4", "4:5", "1:60"})    // each value becomes a separate test run
void parseRejectsInvalidText(String text) { ... }
```

| AssertJ | Checks |
|---|---|
| `assertThat(a).isEqualTo(b)` | `a.equals(b)`: value equality |
| `assertThat(a).isSameAs(b)` / `isNotSameAs` | `a == b`: same object (used to prove "two objects, one value") |
| `assertThat(list).containsExactly(x, y, z)` | elements and order |
| `assertThat(set).hasSize(2)` | size |
| `assertThatThrownBy(() -> ...).isInstanceOf(X.class)` | that the code throws |

**A property test in disguise:** `formatAndParseAreInverses` checks that
`parse(format(x)) == x` for ten values, edge cases included (0, 59, 60, 3599, 3600). Testing a
*property* across inputs catches more bugs than hand-picked examples.

---

## 11. Where this goes in Spring / Masternova ⭐⭐

| Later phase | How this knowledge is used |
|---|---|
| **Every module: `web/dto/`** | Request and response bodies are **records**. Validation annotations go on components: `record CreateCourseRequest(@NotBlank String title, @PositiveOrZero long priceMinor) {}`. Jackson serializes and deserializes records out of the box. |
| **Phase 5 catalog** | `Money` becomes a JPA **`@Embeddable`**, stored as two columns (`price_minor`, `price_currency`) inside the `Course` table. Hibernate 6.2+ supports records as embeddables. |
| **Phase 5–6 catalog** | `LectureDuration` lives on `Lecture`, and section totals use `total(...)` |
| **Phase 9 commerce** | `percentOff` for coupons, `allocate` for instructor revenue splits, `times` for quantities |
| **Phase 2+ events** | Domain events are records: `record OrderPaid(OrderId orderId, Money amount, Instant at) {}` |
| **JPA entities** | **Not** records: Hibernate needs a no-arg constructor, mutable fields and proxies. Entities are classes that *contain* value-object records. |

In Phase 5 the real `Money` class gets `@DesignPattern(value = Pattern.VALUE_OBJECT, ...)` and
joins the [pattern catalog](../README.md).

---

## 12. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | Why |
|---|---|---|
| `double price` | `long priceMinor` + currency | binary floating point is inexact (§6.1) |
| `new BigDecimal(0.1)` | `new BigDecimal("0.1")` | the double is already inexact |
| `bd1.equals(bd2)` for amounts | `bd1.compareTo(bd2) == 0` | `equals` also compares scale (§6.2) |
| `a + b` on large longs | `Math.addExact(a, b)` | silent overflow (§6.3) |
| `return (int) (a - b);` in `compareTo` | `Long.compare(a, b)` | overflow + truncation give the wrong sign |
| `obj1 == obj2`, `str == "x"` | `obj1.equals(obj2)`, `"x".equals(str)` | `==` compares references (§2) |
| validation scattered in callers | validation in the compact constructor | one place, impossible to bypass (§3.2) |
| record with a raw `List` component | `List.copyOf` in the compact constructor | shallow immutability (§5) |
| overriding `equals` but not `hashCode` | both, or neither (let the record do it) | breaks `HashMap`/`HashSet` (§4) |
| `bd.add(x);` ignoring the result | `bd = bd.add(x);` | immutable types return new objects (§5) |
| `Pattern.compile(...)` inside a method called often | `static final Pattern` | compiling is expensive; `Pattern` is thread-safe |
| catching and rethrowing without the cause | `throw new X(msg, cause)` | keeps the original stack trace |
| naming a type `Duration`, `List`, `Date` | domain names: `LectureDuration` | clashes with JDK types |

---

## 13. Interview Q&A ⭐⭐⭐

**Q1. What's the difference between an entity and a value object?**
An entity has identity and a lifecycle, and equality is by id. A value object has no identity,
is immutable, and is compared by value. An entity *contains* value objects: `Course` has a
`Money price`.

**Q2. What does a Java record give you, and what can't it do?**
It gives private final fields, a canonical constructor, accessors, and
`equals`/`hashCode`/`toString` over all components, and the class is `final`. It can't extend a
class, declare instance fields beyond its components, or be mutable. It *can* implement
interfaces and have static members and methods.

**Q3. Where do you validate a record's data?**
In the compact constructor. Every creation path goes through it, so no invalid instance can
exist.

**Q4. Explain the `equals`/`hashCode` contract. What happens if you break it?**
Equal objects must have equal hash codes, and both must be stable while the object is in a hash
collection. Break it and `HashSet` holds duplicates, or `HashMap.get` returns `null` for a key
that is in the map.

**Q5. Why should `HashMap` keys be immutable?**
The hash picks the bucket at insertion. If the key mutates, its hash changes, lookups go to the
wrong bucket, and the entry is lost but still takes up memory.

**Q6. Is a record always immutable?**
Only shallowly. `final` freezes references, not the objects they point to. A `List` component
can still be mutated unless you defensively copy it with `List.copyOf`.

**Q7. How do you represent money in Java?**
Integer minor units (`long`) plus a `Currency`. Use `BigDecimal` from strings only for parsing
and display, never `double`. Use `Math.*Exact` against overflow, choose a rounding rule
explicitly, and allocate remainders when splitting.

**Q8. `new BigDecimal("1.0").equals(new BigDecimal("1.00"))`?**
`false`, because `equals` compares the value *and* the scale. Use `compareTo` for numeric
equality.

**Q9. How do you split ₹10 among 3 people?**
Integer-divide to 333 each, then give the remainder (1 paisa) to the first share: [334, 333,
333]. Never divide in floating point and round each share; the shares won't add up.

**Q10. What's wrong with `return (int) (this.amount - other.amount);` in `compareTo`?**
For large values the subtraction overflows and the cast truncates, so the sign can be wrong and
sorting silently breaks. Use `Long.compare`.

**Q11. `==` vs `equals`?**
`==` compares references for objects (and values for primitives). `equals` compares logical
equality. Use `equals` for strings, records and `BigDecimal` (well, `compareTo` for that last one).

**Q12. Why is immutability good for concurrency?**
State that never changes can't be raced on. Immutable objects are thread-safe without locks
and can be shared freely, even across thousands of virtual threads.

---

## 14. Play with it in `jshell` ⭐

`jshell` is Java's REPL: try any snippet instantly, with no class or `main` needed.

```bash
cd patterns/lab && ./mvnw -q compile
jshell --class-path target/classes
```

```java
jshell> import com.masternova.java.valueobject.*
jshell> var a = Money.of(100, "INR")
jshell> var b = Money.of(100, "INR")
jshell> a == b
jshell> a.equals(b)
jshell> a
jshell> Money.parse("1499.5", "INR").display()
jshell> Money.of(1000, "INR").allocate(3)
jshell> Money.of(1000, "INR").allocate(3).add(a)       // UnsupportedOperationException: why?
jshell> 0.1 + 0.2
jshell> new java.math.BigDecimal(0.1)
jshell> Long.MAX_VALUE + 1
jshell> LectureDuration.parse("1:02:03").format()
jshell> record Point(int x, int y) {}                  // records work in jshell too
jshell> new Point(1, 2).equals(new Point(1, 2))
jshell> /exit
```

---

## 15. 30-second recall

- **Value object:** no id, immutable, equal by value. In Java that's a `record`.
- **Record:** final class, `private final` fields, accessors without `get`, and
  `equals`/`hashCode`/`toString` over all components. It can implement interfaces but not
  extend a class.
- **Compact constructor:** the one place the rules live, so an invalid instance can't exist.
- **`equals`/`hashCode`:** equal objects need equal hashes, and keys must be immutable.
  `==` compares references.
- **Immutability:** operations return new objects. Thread-safe and shareable. Watch for
  shallow immutability and use `List.copyOf`.
- **Money:**
  - `long` minor units plus a `Currency`, never `double`.
  - `BigDecimal` from strings, compared with `compareTo`.
  - `Math.*Exact` against overflow.
  - Split with integer division and hand out the remainder.
- **Next:** [02 — Sealed types & pattern matching](README.md) (task 1.2).
