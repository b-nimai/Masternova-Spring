# 09 — Spring AOP & Proxies (and the `@Transactional` traps)

> **One-liner:** `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize` and every `@Aspect`
> work by putting a **proxy** in front of your bean. The proxy only sees calls that come **from
> outside the bean**, through the proxy. Once you understand that, the famous "it silently
> didn't work" bugs (self-invocation, `final`, `private`, checked exceptions) all have the same
> explanation.

**Roadmap:** task 1.9 · **Last updated:** 2026-10-02 · **Prev:** [08 — Spring IoC & DI](08-spring-ioc-and-di.md)
**Learning tests:** [`backend/api/src/test/.../learning/aop/`](../../backend/api/src/test/java/com/masternova/api/learning/aop/): `ProxyMechanicsLearningTest` · `AspectLearningTest` · `TransactionalProxyLearningTest`
**Pattern:** [Proxy (catalog row 15)](../docs/15-proxy.md), with a Spring-free lab in [`lab/.../patterns/proxy/`](../lab/src/main/java/com/masternova/patterns/proxy/)
**Run:** `cd backend && ./mvnw test -pl api -am -Dtest='ProxyMechanicsLearningTest,AspectLearningTest,TransactionalProxyLearningTest' -Dsurefire.failIfNoSpecifiedTests=false`

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know.

| # | Section | Priority |
|---|---|---|
| 1 | [AOP: the problem it solves](#1-aop-the-problem-it-solves-) | ⭐⭐ |
| 2 | [What a proxy physically is: JDK vs CGLIB](#2-what-a-proxy-physically-is-jdk-vs-cglib-) | ⭐⭐⭐ |
| 3 | [Writing an aspect](#3-writing-an-aspect-) | ⭐⭐ |
| 4 | [The self-invocation trap](#4-the-self-invocation-trap-) | ⭐⭐⭐ |
| 5 | [`@Transactional` in depth](#5-transactional-in-depth-) | ⭐⭐⭐ |
| 6 | [All the ways a proxy feature silently does nothing](#6-all-the-ways-a-proxy-feature-silently-does-nothing-) | ⭐⭐⭐ |
| 7 | [Walkthrough: the learning tests](#7-walkthrough-the-learning-tests-) | ⭐⭐ |
| 8 | [In Masternova](#8-in-masternova-) | ⭐⭐ |
| 9 | [Interview Q&A](#9-interview-qa-) | ⭐⭐⭐ |
| 10 | [30-second recall](#10-30-second-recall) | ⭐⭐⭐ |

---

## 1. AOP: the problem it solves ⭐⭐

Some concerns cut across *every* service: transactions, security checks, caching, timing and
logging, retries, metrics. Writing them by hand into each method means duplication, plus
business logic buried under plumbing:

```java
public void enroll(String id) {
  var tx = txManager.getTransaction(definition);   // plumbing
  try {
    … the one line that matters …
    txManager.commit(tx);                           // plumbing
  } catch (RuntimeException e) { txManager.rollback(tx); throw e; }   // plumbing
}
```

**Aspect-Oriented Programming** moves a cross-cutting concern into one place (an **aspect**) and
applies it declaratively (`@Transactional`) to many methods.

| Term | Meaning | In `ExecutionTimeAspect` |
|---|---|---|
| **Aspect** | the module holding the concern | the `@Aspect` class |
| **Advice** | the code that runs (`@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`) | the `time(...)` method |
| **Pointcut** | *which* methods it applies to | `@annotation(...LogExecutionTime)` |
| **Join point** | one specific method execution | the `ProceedingJoinPoint call` |
| **Weaving** | attaching aspects to code | Spring: at **runtime**, by **proxies** |

---

## 2. What a proxy physically is: JDK vs CGLIB ⭐⭐⭐

`ProxyMechanicsLearningTest` builds both by hand.

**JDK dynamic proxy:** a class *generated at runtime* that **implements the interface** and sends
every call to an `InvocationHandler`:

```java
PriceService proxy = (PriceService) Proxy.newProxyInstance(loader, new Class<?>[] {PriceService.class},
    (p, method, args) -> { log("before"); Object r = method.invoke(target, args); log("after"); return r; });
proxy instanceof CatalogPriceService   // false: it only implements the interface
```

**CGLIB proxy:** a generated **subclass** of your class (name contains `$$SpringCGLIB$$`), which
overrides each method to run the advice and then call the target:

| | JDK dynamic proxy | CGLIB subclass |
|---|---|---|
| Needs | an interface | a non-`final` class with non-`final` methods |
| The proxy is-a | the interface only | your class (and its interfaces) |
| Spring Boot default | — | ✅ `spring.aop.proxy-target-class=true`: CGLIB even when there is an interface |
| Can't handle | classes without interfaces | **`final` classes and methods** (`aFinalClassCannotGetAClassBasedProxy` → `AopConfigException … final`), `private` methods |

**Two consequences you must remember:**

1. **The bean you get from the container is the proxy**, not your object:
   `service.getClass().getName()` contains `$$SpringCGLIB$$` (`callsThroughTheProxyAreAdvised`).
2. **Only method calls are intercepted, never field access.** A CGLIB proxy instance is created
   *without running your constructor*, so its own fields are empty. Reading a field directly on
   the proxy gives `null`, while calling a method reaches the real object
   (`fieldsAreNotProxiedOnlyMethodsAre`). We hit this while writing the tests. Always access
   state through methods.

---

## 3. Writing an aspect ⭐⭐

```java
@Aspect
@Component                                             // (a @Bean in the test)
class ExecutionTimeAspect {
  @Around("@annotation(com.masternova…LogExecutionTime)")      // WHERE: methods carrying the annotation
  Object time(ProceedingJoinPoint call) throws Throwable {     // WHAT: runs around each call
    long start = System.nanoTime();
    try {
      return call.proceed();                           // ⭐ run the real method. Forget this and it never runs.
    } finally {
      log(call.getSignature().getName() + " took …");
    }
  }
}
```

**Pointcut expressions you'll see:**

| Expression | Matches |
|---|---|
| `@annotation(com.x.LogExecutionTime)` | methods with that annotation (our style: explicit, greppable) |
| `@within(org.springframework.stereotype.Service)` | all methods of classes annotated `@Service` |
| `execution(* com.masternova.api..*Service.*(..))` | method-name patterns. Powerful but fragile, so avoid them. |
| `bean(*Repository)` | by bean name |

**Our rule:** prefer the built-in proxy features (`@Transactional`, `@Cacheable`, Micrometer's
`@Observed` in D3) over custom aspects. Write a custom aspect only for a genuine cross-cutting
concern, triggered by an **explicit annotation**, never by broad name patterns.

---

## 4. The self-invocation trap ⭐⭐⭐

```java
class CourseService {
  @LogExecutionTime public String publish(String id) { … }
  public List<String> publishAll(List<String> ids) {
    return ids.stream().map(this::publish).toList();   // ⚠️ `this` = the RAW object, not the proxy
  }
}
```

```text
caller ──► [ proxy ] ──► target.publishAll() ──► this.publish() ──► target.publish()
               ▲                                         │
               └── advice runs only for calls that       └── never passes through the proxy
                   come in through here
```

`selfInvocationBypassesTheAspect`: two `publish` calls happen, and the aspect logs **nothing**.
The same mechanism makes `@Transactional`, `@Cacheable` and `@Async` silently do nothing on
internal calls. **No error, no warning.**

**Fixes, best first:**

1. **Move the method to another bean** (`BatchEnroller` in the tests). The call now crosses a
   bean boundary, through the proxy. Usually also the better design.
2. **Programmatic API:** `TransactionTemplate` (§5), with no proxy involved.
3. Inject the bean into itself (`ObjectProvider<Self>`) and call through it. This works but is
   ugly.
4. (AspectJ compile-time weaving: no proxies, no trap. Overkill for us.)

---

## 5. `@Transactional` in depth ⭐⭐⭐

`TransactionalProxyLearningTest` swaps the real database for a **`RecordingTransactionManager`**
that logs `begin`, `commit` and `rollback`, so you can *see* what the proxy decides:

| Scenario | Recorded | Test |
|---|---|---|
| a call from outside, through the proxy | `begin, commit`, and a transaction is active inside | `aCallThroughTheProxyRunsInATransaction` |
| `enrollBatch` → `this.enroll` ×2 | **nothing**: no transaction | `selfInvocationSilentlyRunsWithoutATransaction` |
| another bean calls `enroll` ×2 | `begin, commit, begin, commit`: one transaction **per call** | `callingFromAnotherBeanGoesThroughTheProxy` |
| `TransactionTemplate` around the batch | `begin, commit`: **one** transaction for the whole batch | `transactionTemplateNeedsNoProxy` |
| unchecked exception | `begin, rollback` | `uncheckedExceptionsRollBack` |
| **checked exception** | **`begin, commit`** ⚠️⚠️ the method failed and the data was committed | `checkedExceptionsCommitByDefault` |
| checked + `rollbackFor = Exception.class` | `begin, rollback` | `rollbackForMakesCheckedExceptionsRollBack` |

**The settings you'll use:**

| Attribute | Meaning |
|---|---|
| `propagation = REQUIRED` (default) | join the current transaction, or start one |
| `propagation = REQUIRES_NEW` | suspend the current one and run in a **separate** transaction. For example, write an audit row even if the outer transaction rolls back. |
| `readOnly = true` | hint for queries: Hibernate skips dirty checking, and the DB may route to a replica |
| `rollbackFor` / `noRollbackFor` | change the exception rules (§5 table) |
| `timeout` | seconds before the transaction is aborted |
| `isolation` | DB isolation level (default: the database's, READ COMMITTED for Postgres) |

**Where to put it:** on **service** methods, the unit of work, never on controllers or
repositories. Keep transactions **short**: no HTTP calls or file uploads inside one, because a
transaction holds a database connection the whole time (and with virtual threads, connections
are the scarce resource: note 07 §9).

**`TransactionTemplate`** (programmatic): when the transaction boundary isn't a whole method, or
inside the same class:

```java
transactionTemplate.executeWithoutResult(status -> { … several steps … });
```

---

## 6. All the ways a proxy feature silently does nothing ⭐⭐⭐

| Cause | Why | Fix |
|---|---|---|
| **self-invocation** (`this.method()`) | the call never passes through the proxy | another bean / `TransactionTemplate` (§4) |
| **`private` method** | a subclass can't override it, so CGLIB can't intercept | make it public (or package-private/protected with CGLIB in Spring 6+) on a bean method called from outside |
| **`final` method** | can't be overridden | remove `final` |
| **`final` class / record** | can't be subclassed; the proxy fails to create | not `final` (services are plain classes; records are for data) |
| **object created with `new`** | not a bean, so no proxy at all | let Spring create it |
| **a checked exception** with `@Transactional` | the default rule commits on checked exceptions | unchecked domain exceptions (note 05), or `rollbackFor` |
| **catching the exception inside** the transactional method | the proxy never sees it, so it commits | let it propagate, or `setRollbackOnly()` |
| **`@Async` method called internally** | self-invocation, so it runs synchronously | another bean |
| **`@Cacheable` called internally** | self-invocation, so there's no cache hit | another bean |
| **wrong `@Transactional` import** | `jakarta.transaction.Transactional` works but has fewer options | use `org.springframework.transaction.annotation.Transactional` |

---

## 7. Walkthrough: the learning tests ⭐⭐

| Test | Shows |
|---|---|
| `ProxyMechanicsLearningTest` | a JDK proxy by hand (`InvocationHandler`); Spring's `ProxyFactory` choosing JDK vs CGLIB; `$$SpringCGLIB$$`; `final` → `AopConfigException` |
| `AspectLearningTest` | a real `@Aspect` with `@Around` on a custom annotation; the bean is a proxy; self-invocation bypasses the aspect |
| `TransactionalProxyLearningTest` | the recording transaction manager; self-invocation; the two fixes; rollback rules for unchecked, checked and `rollbackFor`; fields aren't proxied |

**Testing trick worth stealing:** to test framework behaviour, replace the expensive collaborator
(the DB transaction manager) with a **recording fake**. The same idea as `FakeTransport` in note
06 and `RecordingTransactionManager` here.

---

## 8. In Masternova ⭐⭐

| Phase | Proxy feature |
|---|---|
| 2+ | `@Transactional` on every write service method; outbox rows written **in the same transaction** as the state change |
| 3 | `@PreAuthorize` (a protection proxy) on admin/instructor operations |
| 6 | optimistic locking: the `@Transactional` boundary is where the `@Version` conflict surfaces |
| 8 | `@Cacheable` / a cached-repository decorator for entitlements, **called from another bean** |
| 9 | `CheckoutService` (a facade) calls the order and entitlement services, a cross-bean call, so the transactions really apply. `REQUIRES_NEW` for audit records. |
| D3 | Micrometer `@Observed` / `@Timed` (built-in aspects) instead of a custom timing aspect |

---

## 9. Interview Q&A ⭐⭐⭐

**Q1. How does `@Transactional` work under the hood?**
Spring wraps the bean in a proxy (a CGLIB subclass by default in Boot). The proxy's interceptor
asks the `PlatformTransactionManager` to begin, calls the target, then commits, or rolls back on
unchecked exceptions.

**Q2. Why doesn't `@Transactional` work on a method called from the same class?**
Self-invocation uses `this`, the target, so the proxy is bypassed. Move the method to another
bean or use `TransactionTemplate`.

**Q3. JDK dynamic proxy vs CGLIB?**
Interface-based vs subclass-based. CGLIB can't proxy final classes/methods or private methods.
Spring Boot defaults to CGLIB.

**Q4. Does `@Transactional` roll back on a checked exception?**
No. By default it rolls back only on `RuntimeException`/`Error`. Use `rollbackFor`.

**Q5. `REQUIRED` vs `REQUIRES_NEW`?**
`REQUIRED` joins the existing transaction or creates one. `REQUIRES_NEW` suspends the outer
transaction and commits or rolls back independently.

**Q6. What is AOP? Explain aspect, advice, pointcut, join point.**
It modularises cross-cutting concerns. An aspect is the module. Advice is the code that runs
around/before/after a call. A pointcut selects where it applies. A join point is a specific method
execution.

**Q7. Why must Spring beans and JPA entities not be `final`?**
CGLIB and Hibernate create subclasses for proxies and lazy loading, and a final class can't be
subclassed.

**Q8. Why should transactions be short?**
They hold a DB connection and locks. Long transactions exhaust the pool and block other writers.
Never do remote calls inside one.

---

## 10. 30-second recall

- **AOP:** a cross-cutting concern in one aspect = advice + pointcut, woven by **runtime proxies**.
- **The proxy:**
  - JDK proxies are interface-based; CGLIB proxies are subclasses (Boot's default).
  - The bean you inject is the proxy.
  - Only external method calls are intercepted, never fields.
- **Self-invocation (`this.x()`) bypasses the proxy.** `@Transactional`, `@Cacheable` and `@Async`
  silently do nothing. Fix it with another bean or `TransactionTemplate`.
- **Can't be proxied:** `final` classes and methods, `private` methods, objects created with
  `new`.
- **`@Transactional`:**
  - Rolls back on unchecked exceptions only, so a checked exception **commits**. Use
    `rollbackFor`.
  - `REQUIRED` vs `REQUIRES_NEW`.
  - Goes on service methods. Keep it short, with no remote calls inside.
- **Proxy vs Decorator:** controls access vs adds behaviour; same shape.
- **Next:** [10 — Request lifecycle & JPA fundamentals](10-request-lifecycle-and-jpa.md) (task 1.10).
