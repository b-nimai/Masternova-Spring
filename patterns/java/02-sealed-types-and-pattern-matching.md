# 02 — Sealed Types & Pattern Matching

> **One-liner:** a `sealed` type is a **closed** family: the compiler knows every subtype. A
> pattern-matching `switch` over it can then take values apart (`case Captured(_, Money(long minor, _))`),
> test conditions (`when`), and be **checked for exhaustiveness**. Add a new subtype and the
> compiler points at every place you forgot to handle it.

**Roadmap:** task 1.2 · **Last updated:** 2026-10-02 · **Prev:** [01 — Records & Value Objects](01-records-and-value-objects.md)
**Code:** [`lab/.../java/sealed/`](../lab/src/main/java/com/masternova/java/sealed/): `PaymentOutcome` + `PaymentOutcomes`, `CouponRule` + `Pricing`, `LectureContent` (+ 4 subclasses) + `LectureContents`, `Expr` + `Exprs`
**Tests:** [`lab/.../java/sealed/`](../lab/src/test/java/com/masternova/java/sealed/) · **Run:** `cd patterns/lab && ./mvnw test -Dtest='com.masternova.java.sealed.*Test'`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know. In code, `// ⭐` marks the lines to remember.

| # | Section | Priority |
|---|---|---|
| 1 | [The problem: "one of these shapes"](#1-the-problem-one-of-these-shapes-) | ⭐⭐⭐ |
| 2 | [Sealed types: the rules](#2-sealed-types-the-rules-) | ⭐⭐⭐ |
| 3 | [`switch`: from statement to expression](#3-switch-from-statement-to-expression-) | ⭐⭐⭐ |
| 4 | [Patterns: type, record, nested, guards, `_`](#4-patterns-type-record-nested-guards-_-) | ⭐⭐⭐ |
| 5 | [Exhaustiveness: the whole point](#5-exhaustiveness-the-whole-point-) | ⭐⭐⭐ |
| 6 | [Dominance and `null`](#6-dominance-and-null-) | ⭐⭐ |
| 7 | [Sealed + switch vs polymorphism (Strategy)](#7-sealed--switch-vs-polymorphism-strategy-) | ⭐⭐⭐ |
| 8 | [Sealed + switch vs the Visitor pattern](#8-sealed--switch-vs-the-visitor-pattern-) | ⭐⭐ |
| 9 | [Sealed interface vs enum vs sealed class](#9-sealed-interface-vs-enum-vs-sealed-class-) | ⭐⭐ |
| 10 | [Walkthrough of the code](#10-walkthrough-of-the-code-) | ⭐⭐ |
| 11 | [Where this goes in Spring / Masternova](#11-where-this-goes-in-spring--masternova-) | ⭐⭐ |
| 12 | [Version map: what arrived when](#12-version-map-what-arrived-when-) | ⭐ |
| 13 | [Common mistakes](#13-common-mistakes-) | ⭐⭐⭐ |
| 14 | [Interview Q&A](#14-interview-qa-) | ⭐⭐⭐ |
| 15 | [Play with it in `jshell`](#15-play-with-it-in-jshell-) | ⭐ |
| 16 | [30-second recall](#16-30-second-recall) | ⭐⭐⭐ |

---

## 1. The problem: "one of these shapes" ⭐⭐⭐

A payment attempt ends in **exactly one** of three ways, and each carries different data:

| Outcome | Data |
|---|---|
| Captured | payment id, amount |
| Failed | error code, whether it's worth retrying |
| Pending | payment id, when the approval window expires |

**In TypeScript** you'd write a *discriminated union*, and the compiler narrows on the tag:

```ts
type PaymentOutcome =
  | { kind: 'captured'; paymentId: string; amount: Money }
  | { kind: 'failed'; errorCode: string; retryable: boolean }
  | { kind: 'pending'; paymentId: string; expiresAt: Date };

switch (o.kind) { case 'captured': /* o.amount is known here */ }
```

**In Java before sealed types**, the options were all weak:

- **One class with nullable fields** (`amount` is null unless captured…). Every reader must
  know which fields go with which state. Bugs everywhere.
- **An open interface plus `instanceof` chains.** Anybody can add an implementation, so the
  compiler can't tell you when a chain misses a case:

  ```java
  if (o instanceof Captured) { Captured c = (Captured) o; ... }
  else if (o instanceof Failed) { Failed f = (Failed) o; ... }
  // forgot Pending? compiles fine, silently does nothing 😱
  ```

**Java 21+:** a `sealed interface` of records (the Java version of the TS union) plus a
pattern-matching `switch` (narrowing, and better):

```java
public sealed interface PaymentOutcome {
  record Captured(String paymentId, Money amount) implements PaymentOutcome {}
  record Failed(String errorCode, boolean retryable) implements PaymentOutcome {}
  record Pending(String paymentId, Instant expiresAt) implements PaymentOutcome {}
}

String message = switch (outcome) {
  case Captured c -> "Paid " + c.amount().display();
  case Failed f   -> "Failed: " + f.errorCode();
  case Pending p  -> "Waiting…";
};   // no default, and forgetting a case is a COMPILE error
```

This shape (a closed set of variants, each with its own data) is called an **algebraic data
type (ADT)** or **sum type**. Records are the "product" part (this AND that); sealed is the
"sum" part (this OR that).

---

## 2. Sealed types: the rules ⭐⭐⭐

```java
public abstract sealed class LectureContent permits VideoContent, ArticleContent, QuizContent { … }
public final class VideoContent extends LectureContent { … }        // stops the hierarchy
public final class ArticleContent extends LectureContent { … }      // stops the hierarchy
public non-sealed class QuizContent extends LectureContent { … }    // re-opens it
public class CodingExercise extends QuizContent { … }               // allowed: QuizContent is open
```

| Rule | Detail |
|---|---|
| `sealed` + `permits` lists the only direct subtypes | Works for `interface` and (abstract or concrete) `class` |
| `permits` can be omitted | when every subtype is in the **same file** (e.g. nested records: `PaymentOutcome`) |
| Subtypes must be in the same **package** | or the same named module, if you use Java modules |
| **Every permitted subtype must declare how it continues** | `final` (no more subtypes), `sealed` (its own closed list), or `non-sealed` (open to anyone) |
| Records satisfy that automatically | records are implicitly `final` |
| Enums can implement a sealed interface | enums are implicitly final/sealed |

The compiler enforces all of this. These are the real messages (javac 25):

```text
final class B implements Closed {}       // B is not in `permits`
→ error: class is not allowed to extend sealed class: Closed (as it is not listed in its 'permits' clause)

class Child extends Base {}              // Base is sealed; Child says nothing
→ error: sealed, non-sealed or final modifiers expected
```

**Seeing the seal at runtime** (used in the tests):

```java
PaymentOutcome.class.isSealed();                  // true
PaymentOutcome.class.getPermittedSubclasses();    // [Captured, Failed, Pending]
```

> 💡 **`non-sealed` is a deliberate escape hatch.** `QuizContent` is open, so new quiz kinds
> (`CodingExercise`) can be added without touching `LectureContent`. The cost: a switch can't
> list every quiz subtype, so `case QuizContent q` has to cover them all.

---

## 3. `switch`: from statement to expression ⭐⭐⭐

**The old `switch` statement (still legal):**

```java
String label;
switch (status) {
  case "PAID":
    label = "Paid";
    break;               // forget this and execution "falls through" into the next case 😱
  case "FAILED":
  case "DECLINED":       // fall-through used on purpose to share a branch
    label = "Failed";
    break;
  default:
    label = "Unknown";
}
```

**The modern `switch` expression (Java 14+):**

```java
String label = switch (status) {
  case "PAID" -> "Paid";
  case "FAILED", "DECLINED" -> "Failed";    // several labels, comma-separated
  default -> "Unknown";
};                                           // ← semicolon: it's an expression
```

| | Arrow form `case X ->` | Colon form `case X:` |
|---|---|---|
| Fall-through | **never** | yes, unless you `break` |
| Use | ✅ always prefer this | legacy code |

**`yield`: when a branch needs several statements** (from `Exprs.eval`):

```java
case Var(String name) -> {
  Integer value = env.get(name);
  if (value == null) throw new IllegalArgumentException("unbound variable: " + name);
  yield value;      // ⭐ the block's result; `return` here would return from the whole METHOD
}
```

---

## 4. Patterns: type, record, nested, guards, `_` ⭐⭐⭐

A **pattern** both *tests* a value and *extracts* parts of it into variables.

### 4.1 Type pattern

```java
case Captured c -> c.amount()           // "is it a Captured? then call it c"
```

### 4.2 `instanceof` pattern (Java 16+)

```java
// old: test, then cast by hand
if (outcome instanceof Pending) { Pending p = (Pending) outcome; return now.isAfter(p.expiresAt()); }

// new: test + cast + bind in one go
return outcome instanceof Pending p && now.isAfter(p.expiresAt());
```

⭐ **Flow scoping.** `p` exists only where the compiler *knows* the test succeeded. That's why
it's usable after `&&`, but not after `||`, and not in the `else` branch.

### 4.3 Record pattern (deconstruction, Java 21+)

```java
case Failed(String code, boolean retryable) -> …   // pulls the components out by position
case Failed(var code, var retryable) -> …          // `var` infers the component types
```

The component order is the record's declaration order. Only **records** can be deconstructed
this way; for ordinary classes like `VideoContent`, use a type pattern and call its accessors.

### 4.4 Nested patterns

Patterns compose. `PaymentOutcomes.message` looks *inside* the `Money` inside the `Captured`:

```java
case Captured(_, Money(long minor, _)) when minor == 0 -> "You're enrolled — this one was free!";
//            │  └── a record pattern inside a record pattern
//            └── `_`: ignore paymentId
```

`Exprs.simplify` does the same on a recursive tree:

```java
case Neg(Neg(Expr inner)) -> simplify(inner);       // --x  →  x
```

### 4.5 Guards: `when`

```java
case Failed f when f.retryable() && attemptsSoFar < 3 -> true;
```

The case matches only if the pattern matches **and** the boolean holds. If the guard is
false, matching continues with the next case.

### 4.6 Unnamed patterns `_` (Java 22+)

```java
case Pending _ -> "Waiting…";                    // type matters, the value doesn't
case Failed(String code, _) -> …                 // ignore one component
case Failed _, Captured _ -> false;              // several patterns in one arm (only allowed when they bind no names)
```

### 4.7 Matching two values at once: the `Pair` trick ⭐⭐

Java has no tuple patterns, so `Exprs` wraps the two values in a tiny private record and switches
on that:

```java
private record Pair(Expr left, Expr right) {}

return switch (new Pair(left, right)) {
  case Pair(Num(int a), Num(int b)) -> new Num(a + b);              // constant folding
  case Pair(Num(int zero), Expr other) when zero == 0 -> other;     // 0 + x → x
  case Pair p -> new Add(p.left(), p.right());                      // anything else
};
```

---

## 5. Exhaustiveness: the whole point ⭐⭐⭐

Over a **sealed** type, a switch that handles every permitted subtype is **exhaustive**, so it
needs no `default`. Leave a case out and it doesn't compile:

```java
sealed interface Outcome { record Ok() … ; record Fail() … ; record Wait() … }

return switch (o) { case Ok _ -> "ok"; case Fail _ -> "fail"; };   // Wait missing
→ error: the switch expression does not cover all possible input values
```

**Why this matters on a real team.** Next quarter someone adds `record Refunded(...) implements
PaymentOutcome`. Every switch over `PaymentOutcome` (the message, the retry rule, the label,
analytics, emails) **stops compiling until it's updated**. The compiler hands you the to-do list.

### ⚠️ `default` throws the guarantee away

```java
return switch (outcome) {
  case Captured c -> …;
  default -> "something else";       // ← now Refunded silently lands here. No compile error.
};
```

**Rule:** over a sealed type, list every case and **don't write `default`**.

The same switch over an **open** (non-sealed) interface needs a `default`. The compiler can't
know all the implementations, and gives the same error as above.

⭐ **Runtime safety net.** If a class file is recompiled with a new subtype but the switch isn't,
the JVM throws `MatchException` instead of silently doing nothing.

---

## 6. Dominance and `null` ⭐⭐

### Dominance: order matters, specific before general

Cases are tried **top to bottom**. A case that can never be reached because an earlier one
always matches first is a compile error:

```java
case Captured c -> "any";
case Captured(_, Money(long minor, _)) when minor == 0 -> "free";   // unreachable
→ error: this case label is dominated by a preceding case label
```

So, as in `PaymentOutcomes.message` and `LectureContents`:
- the guarded case comes before the unguarded one;
- the subclass (`CodingExercise`) comes before the superclass (`QuizContent`).

### `null`

```java
switch (outcome) { … }      // outcome == null  →  NullPointerException (as switch always did)

switch (outcome) {
  case null -> "UNKNOWN";    // ⭐ Java 21+: handle null as a case
  case Captured _ -> …
}
```

The test `withoutCaseNullASwitchThrowsOnNull` proves the first behaviour, and
`caseNullTurnsNullIntoAnOrdinaryCase` the second. In domain code, prefer never letting null
reach the switch (validate at the boundary). Use `case null` when null is genuinely meaningful.

---

## 7. Sealed + switch vs polymorphism (Strategy) ⭐⭐⭐

`Pricing.priceAfter` puts all coupon logic in **one switch**. The OO alternative puts it **in
each type**:

```java
// A: data + one switch (what Pricing does)        // B: polymorphism (Strategy pattern)
sealed interface CouponRule {                       sealed interface CouponRule {
  record PercentOff(int p) implements CouponRule {}   Money applyTo(Money price);
  record FlatOff(Money m) implements CouponRule {}    record PercentOff(int p) implements CouponRule {
  record FreeCourse() implements CouponRule {}          public Money applyTo(Money price) { return price.percentOff(p); }
}                                                     }
Money priceAfter(Money price, CouponRule r) {         … one applyTo per record
  return switch (r) { case PercentOff(int p) -> …   }
                      case FlatOff(Money m) -> …
                      case FreeCourse() -> … };
}
```

This trade-off is the **expression problem**:

| | A: sealed + switch | B: polymorphism / Strategy |
|---|---|---|
| Add a new **operation** (e.g. `describe(rule)`, `isStackable(rule)`) | ✅ easy: one new function, other files untouched | ❌ touch every class |
| Add a new **type** (e.g. `BuyOneGetOne`) | ❌ touch every switch, but the **compiler lists them** | ✅ easy: one new class |
| Logic for one operation | in one place, readable top to bottom | spread across classes |
| Types are | plain data | behaviour-rich objects |
| Open to outside extension? | no (closed on purpose) | yes (anyone can implement) |

**Choose:**
- **A** when the set of variants is fixed and stable but you'll keep adding operations:
  outcomes, events, AST nodes, commands, API results.
- **B (Strategy)** when variants keep growing, come from plugins or other modules, or each
  carries heavy behaviour and dependencies. Payment *gateways* in Phase 9 are Strategy, because
  each needs its own SDK client injected. Coupon *rules* are data.

---

## 8. Sealed + switch vs the Visitor pattern ⭐⭐

**Visitor** is the classic GoF way to add operations to a fixed class hierarchy without editing
it:

```java
interface ExprVisitor<R> { R visitNum(Num n); R visitAdd(Add a); R visitMul(Mul m); … }
interface Expr { <R> R accept(ExprVisitor<R> v); }        // every node: return v.visitX(this);
class EvalVisitor implements ExprVisitor<Integer> { … }   // one class per operation
```

That's double dispatch, an `accept` method on every node, a visitor interface, and a class per
operation. With sealed types it collapses to **one function with one switch per operation**
(`Exprs.eval`, `Exprs.simplify`, `Exprs.show`), and the compiler checks exhaustiveness, which
the Visitor gives you through its interface methods.

> **Interview line:** "In modern Java, pattern matching over a sealed hierarchy replaces most
> uses of the Visitor pattern."

---

## 9. Sealed interface vs enum vs sealed class ⭐⭐

| Use | When | Example |
|---|---|---|
| **enum** | a fixed set of **instances** that all have the **same shape** | `Role { LEARNER, INSTRUCTOR, ADMIN }`, `Currency`-like codes |
| **sealed interface + records** | a fixed set of **types**, each with **different data** | `PaymentOutcome`, `CouponRule`, `Expr`, domain events |
| **sealed abstract class** | the variants **share state or code** (fields, constructor logic) | `LectureContent` shares `title` |

Enums also work with pattern-matching switch, and a switch over all constants is exhaustive
without `default`. Enums can even implement a sealed interface.

---

## 10. Walkthrough of the code ⭐⭐

| File | Shows |
|---|---|
| [`PaymentOutcome`](../lab/src/main/java/com/masternova/java/sealed/PaymentOutcome.java) | Sealed interface, nested records, no `permits` (same file), validation in the compact constructors |
| [`PaymentOutcomes`](../lab/src/main/java/com/masternova/java/sealed/PaymentOutcomes.java) | `message`: nested record pattern + guard + type pattern + `_`. `shouldRetry`: guard with `&&`, multi-pattern arm. `statusLabel`: `case null`. `isExpired`: `instanceof` pattern with flow scoping. Also the private-constructor utility class. |
| [`CouponRule`](../lab/src/main/java/com/masternova/java/sealed/CouponRule.java) + [`Pricing`](../lab/src/main/java/com/masternova/java/sealed/Pricing.java) | Why sealed and not an enum; two `FlatOff` cases (guarded, then general); the empty record `FreeCourse()` matched as `case FreeCourse()` |
| [`LectureContent`](../lab/src/main/java/com/masternova/java/sealed/LectureContent.java) and subclasses | Sealed **class** with `permits`; `final` vs `non-sealed`; `super(title)`; a `protected` constructor |
| [`LectureContents`](../lab/src/main/java/com/masternova/java/sealed/LectureContents.java) | Exhaustive switch over a class hierarchy; dominance (`CodingExercise` before `QuizContent`); `Math.ceilDiv` |
| [`Expr`](../lab/src/main/java/com/masternova/java/sealed/Expr.java) + [`Exprs`](../lab/src/main/java/com/masternova/java/sealed/Exprs.java) | Recursive ADT; `yield`; nested patterns `Neg(Neg(…))`; the `Pair` trick; bottom-up simplification; the Visitor replacement |

**The tests worth reading:**
- `theCompilerKnowsEveryPermittedSubtype` (the seal via reflection)
- `withoutCaseNullASwitchThrowsOnNull`
- `simplifyNeverChangesTheValue`: a property, `eval(simplify(e)) == eval(e)`, the best way to
  test any transformation

---

## 11. Where this goes in Spring / Masternova ⭐⭐

| Phase | Use |
|---|---|
| **2 Platform** | Domain exceptions as a sealed hierarchy, mapped to Problem Details by one exhaustive switch in `GlobalExceptionHandler`. A new exception type can't silently become a 500. |
| **6 Authoring** | `sealed interface CurriculumCommand` (AddSection, MoveLecture, …) as records. Jackson turns `{"kind": "ADD_SECTION", …}` into the right record via `@JsonTypeInfo`/`@JsonSubTypes`, and apply/invert are switches. |
| **6 Authoring** | Course lifecycle states as a sealed interface: the State pattern's data-oriented variant |
| **8 Entitlement** | `sealed interface Decision { Allow, Deny(reason), Abstain }` returned by each policy |
| **9 Commerce** | `PaymentOutcome` itself, nearly as written here, and order states |
| **Everywhere** | API results and domain events: `sealed interface CourseEvent permits CoursePublished, CourseArchived` |

---

## 12. Version map: what arrived when ⭐

| Feature | Final in |
|---|---|
| `switch` expressions, arrow labels, `yield` | Java 14 |
| Records · `instanceof` pattern | Java 16 |
| Sealed classes/interfaces | Java 17 (LTS) |
| Pattern matching for `switch`, `case null`, guards `when` · record patterns | Java 21 (LTS) |
| Unnamed variables and patterns `_` | Java 22 |
| Primitive types in patterns (`case int i when i > 0`) | *preview* in Java 25: not used here |

Java 25 (our version) has everything above except the preview feature. In older codebases
(Java 11/17), expect `instanceof` chains and visitors instead.

---

## 13. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | Why |
|---|---|---|
| `default ->` in a switch over a sealed type | list every case | `default` silently swallows new subtypes (§5) |
| general case before specific (`QuizContent` before `CodingExercise`) | specific first | compile error: "dominated" (§6) |
| unguarded `case Captured c` before `case Captured(…) when …` | guarded first | same |
| `return` inside a switch-expression block | `yield value;` | `return` leaves the whole method (§3) |
| colon-form `case X:` without `break` | arrow form `case X ->` | fall-through bugs |
| a subtype of a sealed class with no modifier | add `final`, `sealed` or `non-sealed` | compile error (§2) |
| an enum where variants carry different data | sealed interface + records | enums share one shape (§9) |
| sealed + switch for things that grow by plugin | Strategy (polymorphism) | adding types is the costly direction (§7) |
| `instanceof` + manual cast | `instanceof Type t` | less code, no cast errors |
| deconstructing a normal class `case VideoContent(…)` | type pattern + accessors | only records support record patterns |

---

## 14. Interview Q&A ⭐⭐⭐

**Q1. What is a sealed class/interface, and why use one?**
A type whose direct subtypes are fixed (`permits`, or the same file). It models "exactly one of
these shapes". The compiler knows every subtype, which enables exhaustive pattern matching with
no `default`.

**Q2. What modifiers must a permitted subclass have?**
`final`, `sealed` or `non-sealed`. Records are implicitly final. Subclasses must be in the same
package (or the same module).

**Q3. What does exhaustiveness give you, and how can you lose it?**
Adding a subtype makes every non-exhaustive switch a compile error, so the compiler lists the
places to update. You lose it by adding `default`, or by switching over a non-sealed type.

**Q4. What's a record pattern? A guard? `_`?**
A record pattern deconstructs a record into its components (`case Failed(String code, _)`) and
can be nested. A guard (`when cond`) adds a condition to a case. `_` ignores a component or a
binding (Java 22+).

**Q5. What happens when you switch on `null`?**
`NullPointerException`, unless there's a `case null` (Java 21+).

**Q6. What's dominance?**
An earlier case that matches everything a later case would match makes the later one
unreachable. That's a compile error, so put specific and guarded cases first.

**Q7. Sealed + switch or polymorphism?**
It's the expression problem. Sealed + switch makes new operations cheap and new types costly,
but compiler-checked; good for closed sets like events, results and ASTs. Polymorphism
(Strategy) makes new types cheap and new operations costly; good for open, plugin-like sets.

**Q8. How does pattern matching relate to the Visitor pattern?**
It replaces it in most cases: one function with one exhaustive switch per operation, instead
of `accept` and `visit` double dispatch.

**Q9. Enum vs sealed interface?**
Use an enum for a fixed set of same-shaped instances. Use a sealed interface for a fixed set of
types with different data.

**Q10. What does "flow scoping" mean for `instanceof` patterns?**
The binding variable is in scope only where the test is known to be true: after `&&`, inside
the `if`, or after an early return on `!(x instanceof T t)`.

---

## 15. Play with it in `jshell` ⭐

```bash
cd patterns/lab && ./mvnw -q compile && jshell --class-path target/classes
```

```java
jshell> import com.masternova.java.sealed.*
jshell> import com.masternova.java.sealed.PaymentOutcome.*
jshell> import com.masternova.java.valueobject.*
jshell> PaymentOutcomes.message(new Failed("BANK_TIMEOUT", true))
jshell> PaymentOutcomes.message(new Captured("p", Money.zero("INR")))
jshell> PaymentOutcome.class.getPermittedSubclasses()
jshell> import com.masternova.java.sealed.Expr.*
jshell> var e = new Mul(new Add(new Var("x"), new Num(0)), new Num(1))
jshell> Exprs.show(e)
jshell> Exprs.show(Exprs.simplify(e))
jshell> sealed interface Light permits Red, Green {}      // try the compile errors yourself
jshell> record Red() implements Light {}
jshell> record Green() implements Light {}
jshell> String go(Light l) { return switch (l) { case Red _ -> "stop"; }; }   // → not exhaustive
```

---

## 16. 30-second recall

- **Sealed type:** a closed family (`permits`, or the same file). Subtypes are `final`,
  `sealed` or `non-sealed`; records are final.
- **Switch expression:** arrows, no fall-through, `yield` in blocks, ends with `;`.
- **Patterns:**
  - type pattern: `case Captured c`
  - record pattern: `case Failed(var code, _)`
  - nested pattern: `Captured(_, Money(long m, _))`
  - guard: `when …`
  - null case: `case null`
- **Exhaustiveness:** a switch over a sealed type needs no `default`, and **must not have
  one**. Then a new subtype is a compile error at every switch.
- **Ordering:** specific and guarded cases first (dominance).
- **Design choices:**
  - Closed set plus growing operations: sealed + switch.
  - Open set: Strategy.
  - Sealed + switch replaces Visitor.
  - Same shape: enum. Different shapes: sealed + records.
- **Next:** [03 — Collections, Streams & Collectors](03-collections-and-streams.md) (task 1.3).
