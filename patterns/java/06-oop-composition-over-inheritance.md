# 06 — OOP in Production: Composition over Inheritance

> **One-liner:** inheritance couples you to your parent's *internals*. Composition (an object
> that **has** and **delegates to** another) couples you only to its *interface*. Default to
> interfaces + composition (Strategy, Decorator). Use inheritance only for a class **designed**
> to be extended (Template Method), and make everything else `final`.

**Roadmap:** task 1.6 · **Last updated:** 2026-10-02 · **Prev:** [05 — Exceptions & `Optional`](05-exceptions-and-optional.md)
**Code:** [`lab/.../java/oop/`](../lab/src/main/java/com/masternova/java/oop/): `inheritance/` (fragile vs composed), `notify/` (Channel + decorators), `template/` (Template Method), `encapsulation/Cart`, `dispatch/DispatchTraps`
**Tests:** [`lab/.../java/oop/`](../lab/src/test/java/com/masternova/java/oop/) · **Run:** `cd patterns/lab && ./mvnw test -Dtest='com.masternova.java.oop.*Test'`

> **Basics first?** Your own notes in [`../../../OOPs/`](../../../OOPs/0.%20Index.md) already
> cover encapsulation, access modifiers, inheritance, polymorphism, abstraction, interfaces,
> relationships and SOLID. This note **assumes them** and shows how they behave in production
> code, with tests.

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know. In code, `// ⭐` marks the lines to remember.

| # | Section | Priority |
|---|---|---|
| 1 | [Why "favour composition" is the default](#1-why-favour-composition-is-the-default-) | ⭐⭐⭐ |
| 2 | [The fragile base class, live](#2-the-fragile-base-class-live-) | ⭐⭐⭐ |
| 3 | [The class explosion → decorators](#3-the-class-explosion--decorators-) | ⭐⭐⭐ |
| 4 | [When inheritance IS right: Template Method](#4-when-inheritance-is-right-template-method-) | ⭐⭐⭐ |
| 5 | [Interface vs abstract class (in 2026 Java)](#5-interface-vs-abstract-class-in-2026-java-) | ⭐⭐⭐ |
| 6 | [Encapsulation = protecting invariants](#6-encapsulation--protecting-invariants-) | ⭐⭐⭐ |
| 7 | [Which method runs? Dispatch traps](#7-which-method-runs-dispatch-traps-) | ⭐⭐⭐ |
| 8 | [SOLID, in this code](#8-solid-in-this-code-) | ⭐⭐⭐ |
| 9 | [Strategy vs Decorator vs Template Method](#9-strategy-vs-decorator-vs-template-method-) | ⭐⭐⭐ |
| 10 | [Walkthrough of the code](#10-walkthrough-of-the-code-) | ⭐⭐ |
| 11 | [In Spring / Masternova](#11-in-spring--masternova-) | ⭐⭐ |
| 12 | [Common mistakes](#12-common-mistakes-) | ⭐⭐⭐ |
| 13 | [Interview Q&A](#13-interview-qa-) | ⭐⭐⭐ |
| 14 | [30-second recall](#14-30-second-recall) | ⭐⭐⭐ |

---

## 1. Why "favour composition" is the default ⭐⭐⭐

| | **Inheritance** (`extends`) | **Composition** (has-a field + delegate) |
|---|---|---|
| Couples you to | the parent's **implementation**: which methods call which, internal state | the collaborator's **interface** only |
| Fixed at | compile time: one parent, forever | runtime: pass in any implementation |
| Combining features | one subclass per combination (explosion, §3) | stack wrappers in any order |
| Testing | hard to test the child without the parent | inject a fake (`FakeTransport`) |
| Breaks when | the parent changes internally (§2) | only when the interface changes |
| Java limit | one superclass | unlimited collaborators |

**Use inheritance only when all of these hold:**

1. It's a true **is-a** relationship, **and**
2. the subclass is fully substitutable for the parent (Liskov, §8), **and**
3. the parent was **designed and documented for extension** (Template Method, §4), usually within
   your own module.

Otherwise: an interface plus composition. Make classes **`final` by default**. Every class in the
lab that isn't meant to be extended is `final`.

---

## 2. The fragile base class, live ⭐⭐⭐

The goal: a tag set that counts how many tags were ever added.

```java
public class InstrumentedTagSet extends HashSet<String> {        // ❌ inheritance
  private int addCount;
  @Override public boolean add(String tag) { addCount++; return super.add(tag); }
  @Override public boolean addAll(Collection<? extends String> tags) {
    addCount += tags.size();
    return super.addAll(tags);            // HashSet.addAll calls this.add(...) for each tag!
  }
}

tags.addAll(List.of("java", "spring", "docker"));
tags.addCount();    // 6, not 3 ❌   (CompositionOverInheritanceTest.inheritanceDoubleCounts…)
```

**What went wrong:** `HashSet.addAll` happens to be implemented by calling `add`, a
*self-use* detail of the parent. Our override of `add` intercepts those internal calls. We didn't
change anything, and the parent's implementation broke us. That is **the fragile base class
problem**. "Fixing" it by not overriding `addAll` would *depend* on that detail, and break the
day the JDK changes it.

**Composition can't have this bug:**

```java
public final class CountingTagSet {
  private final Set<String> tags = new HashSet<>();       // ⭐ has-a
  public boolean addAll(Collection<String> newTags) {
    addCount += newTags.size();
    return tags.addAll(newTags);         // HashSet calls ITS add(), never ours
  }
}
tags.addCount();    // 3 ✅
```

---

## 3. The class explosion → decorators ⭐⭐⭐

Notifications need channels (email, SMS) **and** cross-cutting features (retry, quiet hours,
logging…). With inheritance:

```text
Notifier
 ├── EmailNotifier
 │    ├── RetryingEmailNotifier
 │    │    └── QuietHoursRetryingEmailNotifier
 │    └── QuietHoursEmailNotifier
 └── SmsNotifier
      ├── RetryingSmsNotifier …              2 channels × 2² feature combos = 8 classes. Add push + logging → 24.
```

**With composition**, everything is a `Channel`, and features are wrappers that are *also*
`Channel`s. That's the **Decorator** pattern:

```java
public interface Channel { Delivery send(Notification n); String name(); }

final class EmailChannel implements Channel { … }                       // does one thing
final class RetryingChannel implements Channel {                         // ⭐ IS a Channel…
  private final Channel inner;                                           // ⭐ …and HAS a Channel
  public Delivery send(Notification n) { /* try inner.send(n) up to N times */ }
}

Channel channel =
    new QuietHoursChannel(                   // outermost: decides whether to send at all
        new RetryingChannel(                 // then: retries
            new EmailChannel(smtp), 3),      // innermost: actually sends
        clock, IST, LocalTime.of(22, 0), LocalTime.of(8, 0));
```

- **2 + 2 classes cover every combination**, in any order, decided at runtime.
- Each class has one job, and each is tested alone (`ChannelDecoratorTest`).
- `QuietHoursChannel` takes a `Clock`, so the test can say "it's 23:30 IST" and check the
  deferral to 08:00 the next day, including the window that wraps midnight.

This is the same shape as `java.io`: `new BufferedReader(new InputStreamReader(new
FileInputStream(f)))`. In Masternova, the cached entitlement repository (Phase 8) and the progress
write-back buffer (Phase 10) are decorators.

---

## 4. When inheritance IS right: Template Method ⭐⭐⭐

Every Masternova email has the same **skeleton** (greeting → body → footer → unsubscribe line),
and only some **steps** vary. That's the textbook case for inheritance done right:

```java
public abstract class EmailTemplate {
  public final String render(String learner) {          // ⭐ final: the skeleton is fixed
    return "Hi " + learner + ",\n\n" + body() + "\n\n" + footer() + "\n--\nUnsubscribe: …" + unsubscribeTopic();
  }
  public abstract String subject();                      // ⭐ steps subclasses MUST provide
  protected abstract String body();
  protected String footer() { return "Happy learning,…"; }   // ⭐ hook: optional override
  protected abstract String unsubscribeTopic();
}

public final class ReceiptEmail extends EmailTemplate { … overrides body() and footer() }
```

**Why this is safe, when `InstrumentedTagSet` wasn't:**

| Fragile inheritance | Template Method |
|---|---|
| the parent wasn't written for subclassing | the parent is **designed** for it |
| subclasses override methods the parent calls internally, *unknowingly* | the parent calls **only** the documented abstract steps and hooks |
| the parent's flow can be broken | the skeleton is **`final`**: the unsubscribe line can't be dropped (`theSkeletonCannotBeOverridden`) |
| leaf classes left open | leaf classes are **`final`** |

Phase 4 builds the real email templates (Thymeleaf) and Phase 7 the pipeline job base class
exactly this way.

---

## 5. Interface vs abstract class (in 2026 Java) ⭐⭐⭐

| | `interface` | `abstract class` |
|---|---|---|
| State (instance fields) | ❌ (only `public static final` constants) | ✅ |
| Constructors | ❌ | ✅ (called via `super(...)`) |
| Method bodies | `default`, `static`, `private` methods (Java 8/9+) | any |
| A class can have | **many** | **one** |
| Can be `sealed` | ✅ | ✅ |
| Use for | **capabilities and contracts**: `Channel`, `Comparable`, `Keyed` | **shared state + a fixed skeleton**: `EmailTemplate`, `LectureContent` |

**Default rule:** reach for an interface. Use an abstract class when subclasses genuinely share
*state* or a *template*.

**`default` methods** let an interface grow without breaking implementers. But if two interfaces
supply the **same** default method, the class must choose:

```java
class PaidCourse implements Purchasable, Shareable {}
→ error: types Purchasable and Shareable are incompatible; (class inherits unrelated defaults for label())

public String label() { return Purchasable.super.label() + "+" + Shareable.super.label(); }   // ⭐ explicit
```

---

## 6. Encapsulation = protecting invariants ⭐⭐⭐

Your note 1 defines encapsulation as private fields plus getters/setters. **In production that's
not enough.** A class with a getter and a blind setter for every field protects nothing. Real
encapsulation means **the object enforces its own rules**, and there's no way around them.

`Cart` (a **rich domain model**):

```java
public final class Cart {
  private final List<Course> items = new ArrayList<>();   // ⭐ private AND never handed out

  public void add(Course course) {                         // the ONLY way in
    if (!course.published()) throw new IllegalStateException(…);
    if (contains(course.id())) throw new IllegalStateException(…);     // no duplicates
    if (items.size() == MAX_ITEMS) throw new IllegalStateException(…); // max 20
    if (/* different currency */) throw new IllegalStateException(…);
    items.add(course);
  }
  public Money total() { … }                               // ⭐ derived: can never be "wrong"
  public List<Course> items() { return List.copyOf(items); }   // ⭐ a snapshot, not the live list
}
```

| Anemic model ❌ | Rich model ✅ |
|---|---|
| `cart.getItems().add(course)`: the rules live in some service, or nowhere | `cart.add(course)`: the cart checks |
| `cart.setTotal(x)`: can drift from the items | `total()` is computed |
| "ask" the object for data, then decide outside | **"tell, don't ask"**: tell the object what to do |
| every caller must remember the rules | impossible to break the rules |

**Tools for encapsulation in Java:**

- `private` fields;
- **no setters** unless a rule allows the change;
- defensive copies in and out (`List.copyOf`);
- `final` fields;
- immutable value objects (note 01);
- **package-private** classes (no modifier), so a type is visible only inside its package (its
  Spring Modulith module internals);
- **`final` classes**, so nobody can subclass around your rules.

---

## 7. Which method runs? Dispatch traps ⭐⭐⭐

| Kind | Chosen by | When | Test |
|---|---|---|---|
| **overridden instance method** | the object's **actual class** | runtime ("dynamic dispatch") | `Lecture l = new VideoLecture(); l.kind()` → `"video"` |
| **overloaded method** | the variable's **declared type** | **compile time** | `describe(lecture)` → `describe(Lecture)` even though the object is a `VideoLecture` ⚠️ |
| **field** | the declared type | compile time | `lecture.label` → `"lecture"`, `((VideoLecture) lecture).label` → `"video"`: two fields! |
| **static method** | the declared type ("hiding") | compile time | `@Override` on a static → `error: static methods cannot be annotated with @Override` |

**Override rules the compiler enforces:**

| Rule | Error |
|---|---|
| can't narrow visibility (`public` → package-private) | `k() in WeakerAccess cannot override k() in P2` (attempting to assign weaker access privileges) |
| can't override a `final` method | `render() in OverrideFinal cannot override render() in T` |
| can't extend a `final` class | `cannot inherit from final F` |
| the return type may be **covariant** (a subtype), the parameters must match exactly | otherwise it's an *overload*, not an override. That's why **`@Override` is mandatory style**: it turns a silent overload into a compile error. |

**Constructors and overridable methods:** never call an overridable method from a constructor.
The subclass override runs **before the subclass's fields are initialised**, so it sees nulls.

---

## 8. SOLID, in this code ⭐⭐⭐

(Definitions: your [SOLID note](../../../OOPs/14.%20SOLID%20Principles.md). Here's where each one shows up.)

| Principle | Where in the lab | What it looks like |
|---|---|---|
| **S**ingle responsibility | `EmailChannel` only sends; `RetryingChannel` only retries; `QuietHoursChannel` only checks the time | each class has one reason to change |
| **O**pen/closed | add a `PushChannel` or `LoggingChannel` without editing any existing class | extend by **adding** a class (decorators, Strategy registry from note 04) |
| **L**iskov substitution | every decorator works anywhere a `Channel` does; `Delivery.Failed` instead of a surprise exception | ⚠️ the JDK's own `List.of(...)` **violates** LSP: it's a `List`, but `add` throws `UnsupportedOperationException`. Know that example. |
| **I**nterface segregation | `Channel` has 2 methods; `Keyed<K>` has 1 | Phase 5 splits `CourseReader` / `CourseWriter` |
| **D**ependency inversion | decorators and services depend on `Channel`, not `EmailChannel`; `QuietHoursChannel` depends on `Clock`, not the system time | constructor injection of abstractions, which is exactly what Spring DI does (task 1.8) |

---

## 9. Strategy vs Decorator vs Template Method ⭐⭐⭐

Three patterns that look alike and get mixed up in interviews:

| | **Strategy** | **Decorator** | **Template Method** |
|---|---|---|---|
| Mechanism | composition: hold *one* interchangeable algorithm | composition: wrap an object of the *same* interface | **inheritance**: a parent skeleton + subclass steps |
| Changes | **which** algorithm runs | **adds** behaviour around the same call | **steps** inside a fixed algorithm |
| Chosen | at runtime (injected, or from a registry) | at runtime (stacked) | at compile time (which subclass) |
| Example here | `PaymentGateway` (pattern note 01), `Channel` chosen per learner preference | `RetryingChannel`, `QuietHoursChannel` | `EmailTemplate` → `WelcomeEmail`, `ReceiptEmail` |
| Masternova | payment gateways, auth methods (Phases 3, 9) | entitlement cache, progress buffer (Phases 8, 10) | emails, pipeline jobs (Phases 4, 7) |

**One-line test:**
- "Swap the whole algorithm?" Strategy.
- "Add something before or after, keeping the interface?" Decorator.
- "Same recipe, different ingredients?" Template Method.

---

## 10. Walkthrough of the code ⭐⭐

| File | Shows |
|---|---|
| [`InstrumentedTagSet`](../lab/src/main/java/com/masternova/java/oop/inheritance/InstrumentedTagSet.java) / [`CountingTagSet`](../lab/src/main/java/com/masternova/java/oop/inheritance/CountingTagSet.java) | the fragile base class (counts 6) vs composition (counts 3) |
| [`Channel`](../lab/src/main/java/com/masternova/java/oop/notify/Channel.java), [`EmailChannel`](../lab/src/main/java/com/masternova/java/oop/notify/EmailChannel.java), [`SmsChannel`](../lab/src/main/java/com/masternova/java/oop/notify/SmsChannel.java) | interface + single-responsibility implementations, depending on an injected `FakeTransport` |
| [`RetryingChannel`](../lab/src/main/java/com/masternova/java/oop/notify/RetryingChannel.java), [`QuietHoursChannel`](../lab/src/main/java/com/masternova/java/oop/notify/QuietHoursChannel.java) | Decorators; an injected `Clock`; a time window that wraps midnight; a sealed `Delivery` result instead of exceptions |
| [`EmailTemplate`](../lab/src/main/java/com/masternova/java/oop/template/EmailTemplate.java) + subclasses | Template Method: a `final` skeleton, abstract steps, a hook, `final` leaf classes |
| [`Cart`](../lab/src/main/java/com/masternova/java/oop/encapsulation/Cart.java) | a rich model: invariants inside, a derived total, a defensive copy out |
| [`DispatchTraps`](../lab/src/main/java/com/masternova/java/oop/dispatch/DispatchTraps.java) | overriding vs overloading vs hiding; the default-method diamond |

---

## 11. In Spring / Masternova ⭐⭐

- **Spring is composition-first.** Beans receive collaborators through their constructors
  (task 1.8); you almost never `extends` a Spring class. The few exceptions are
  framework-designed templates like `OncePerRequestFilter`, `ResponseEntityExceptionHandler`
  (our `GlobalExceptionHandler`) and `AbstractRoutingDataSource`, which are Template Methods by
  design.
- **Spring itself uses Decorator/Proxy** for `@Transactional`, `@Cacheable` and `@Async`
  (task 1.9).
- **JPA is the exception that needs non-final classes.** Hibernate subclasses entities at
  runtime (lazy-loading proxies), so entities can't be `final`. Everything else should be.
- **Modulith modules:** a module's internals are **package-private**, which is encapsulation at
  the architecture level.

---

## 12. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead |
|---|---|
| `extends` a concrete class to reuse code | hold it as a field and delegate (§2) |
| one subclass per feature combination | decorators (§3) |
| a non-`final` class "just in case" | `final` unless designed for extension |
| overriding a method the parent calls internally | composition, or a documented hook |
| a template method that isn't `final` | `public final` skeleton, `protected abstract` steps |
| getters + setters for everything ("anemic") | behaviour methods that enforce invariants (§6) |
| returning the internal mutable list | `List.copyOf` / an unmodifiable view |
| forgetting `@Override` | always use it: it turns a silent overload into a compile error |
| expecting overloads to pick the runtime type | overloads are compile-time; use overriding or pattern matching |
| a field in the subclass with the same name as the parent's | rename it: field hiding is never what you want |
| calling an overridable method in a constructor | make it `private` / `final`, or move the call |
| a subclass that throws `UnsupportedOperationException` for parent methods | an LSP violation: split the interface (§8) |

---

## 13. Interview Q&A ⭐⭐⭐

**Q1. Why favour composition over inheritance?**
Inheritance couples you to the parent's implementation (the fragile base class), is fixed at
compile time, and explodes into subclasses when features are combined. Composition depends only
on interfaces, can be swapped at runtime, and features stack. Use inheritance only for a class
designed for extension.

**Q2. What is the fragile base class problem? Give an example.**
A subclass breaks because of internal changes or self-use in its parent. The example: counting
adds in a `HashSet` subclass double-counts, because `addAll` calls `add`.

**Q3. Abstract class vs interface? When do you use each?**
An interface is a contract or capability: many per class, no instance state, `default`/`static`/
`private` methods. An abstract class holds shared state and a skeleton: one per class,
constructors, fields. Default to interfaces.

**Q4. What is the Decorator pattern? How does it differ from inheritance and from a Proxy?**
A wrapper that implements the same interface and adds behaviour around the delegate; wrappers
stack at runtime. Inheritance fixes the behaviour per class. A Proxy has the same shape but
*controls access* (lazy loading, security, transactions) rather than adding features. Spring's
`@Transactional` is a proxy.

**Q5. Template Method vs Strategy?**
Inheritance vs composition. Template Method varies *steps* of a fixed algorithm via subclasses.
Strategy swaps the *whole* algorithm via an injected object.

**Q6. What is encapsulation, beyond private fields?**
Protecting invariants. The object exposes behaviour that enforces its rules, never its mutable
internals, and computes derived values. Tell, don't ask.

**Q7. Overloading vs overriding: which is resolved at runtime?**
Overriding (dynamic dispatch on the actual object). Overloading is resolved at compile time from
the *declared* argument types. Static methods and fields aren't polymorphic.

**Q8. Two interfaces have the same default method. What happens?**
A compile error unless the class overrides it. It can delegate with `X.super.method()`.

**Q9. Give an LSP violation from the JDK.**
`List.of(...)` returns a `List` whose `add` throws `UnsupportedOperationException`. Also the
classic Square-extends-Rectangle example.

**Q10. Why make classes `final` by default?**
Inheritance must be designed for. `final` prevents fragile subclassing, makes behaviour
predictable, and leaves you free to change internals. (The exception is JPA entities, which
need proxying.)

---

## 14. 30-second recall

- **Default to composition:** hold a field and delegate. Interfaces plus constructor-injected
  collaborators. Classes are `final` by default.
- **Fragile base class:** the parent's self-use breaks your override (`addAll` → `add`, so
  counts double).
- **Feature combinations:** use **Decorators**: a class that IS-a and HAS-a `Channel`, stacked
  at runtime. Not a subclass per combination.
- **Inheritance done right:** **Template Method**. A `final` skeleton, abstract steps, optional
  hooks, `final` leaf classes.
- **Interface vs abstract class:** a capability or contract vs shared state and a skeleton.
  Clashing defaults need an explicit override with `X.super.m()`.
- **Encapsulation:** invariants inside, no blind setters, derived values, defensive copies,
  package-private internals.
- **Dispatch:**
  - Overriding: runtime, by the actual object.
  - Overloading: compile time, by the declared type.
  - Fields and statics: not polymorphic.
  - Always write `@Override`.
- **The three lookalikes:** Strategy (swap), Decorator (wrap), Template Method (fill in steps).
- **Next:** [07 — Concurrency & virtual threads](07-concurrency-and-virtual-threads.md) (task 1.7).
