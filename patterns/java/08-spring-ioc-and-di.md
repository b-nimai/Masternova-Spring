# 08 — Spring IoC & Dependency Injection

> **One-liner:** you declare **what** objects exist (beans) and **what they need** (constructor
> parameters). The Spring **container** creates them in the right order, wires them together,
> manages their lifecycle, and fails fast at startup if the wiring is ambiguous or broken.
> You already know this idea from **NestJS**: providers and `@Injectable`. Spring is where
> NestJS got it from.

**Roadmap:** task 1.8 · **Last updated:** 2026-10-02 · **Prev:** [07 — Concurrency](07-concurrency-and-virtual-threads.md)
**Real code:** [`MasternovaProperties`](../../backend/api/src/main/java/com/masternova/api/platform/MasternovaProperties.java) (typed config), [`PlatformConfig`](../../backend/api/src/main/java/com/masternova/api/platform/PlatformConfig.java), [`SecurityConfig`](../../backend/api/src/main/java/com/masternova/api/platform/security/SecurityConfig.java), [`MetaController`](../../backend/api/src/main/java/com/masternova/api/platform/web/MetaController.java)
**Learning tests:** [`backend/api/src/test/.../learning/ioc/`](../../backend/api/src/test/java/com/masternova/api/learning/ioc/): DI resolution · scopes & lifecycle · configuration & conditions · properties binding
**Run:** `cd backend && ./mvnw test -pl api -am -Dtest='*LearningTest' -Dsurefire.failIfNoSpecifiedTests=false`

> Spring code can't live in the Spring-free `patterns/lab` (ADR-0002), so each Spring
> behaviour is proven by a **learning test** in the api module, built on
> **`ApplicationContextRunner`**. It boots a tiny context in milliseconds with no web server
> and no database. It's also how Spring Boot tests its own auto-configuration, and the best way
> to experiment.

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know.

| # | Section | Priority |
|---|---|---|
| 1 | [IoC and DI: the idea, and NestJS → Spring](#1-ioc-and-di-the-idea-and-nestjs--spring-) | ⭐⭐⭐ |
| 2 | [Declaring beans](#2-declaring-beans-) | ⭐⭐⭐ |
| 3 | [Injection styles: constructor wins](#3-injection-styles-constructor-wins-) | ⭐⭐⭐ |
| 4 | [How Spring picks a bean](#4-how-spring-picks-a-bean-) | ⭐⭐⭐ |
| 5 | [Scopes, and the prototype-in-singleton trap](#5-scopes-and-the-prototype-in-singleton-trap-) | ⭐⭐⭐ |
| 6 | [The bean lifecycle](#6-the-bean-lifecycle-) | ⭐⭐ |
| 7 | [`@Configuration`, profiles, conditions, auto-configuration](#7-configuration-profiles-conditions-auto-configuration-) | ⭐⭐⭐ |
| 8 | [Configuration properties](#8-configuration-properties-) | ⭐⭐⭐ |
| 9 | [Walkthrough: the learning tests](#9-walkthrough-the-learning-tests-) | ⭐⭐ |
| 10 | [How Masternova uses this, and the rules for new modules](#10-how-masternova-uses-this-and-the-rules-for-new-modules-) | ⭐⭐⭐ |
| 11 | [Common mistakes](#11-common-mistakes-) | ⭐⭐⭐ |
| 12 | [Interview Q&A](#12-interview-qa-) | ⭐⭐⭐ |
| 13 | [30-second recall](#13-30-second-recall) | ⭐⭐⭐ |

---

## 1. IoC and DI: the idea, and NestJS → Spring ⭐⭐⭐

**Without DI**, a class builds its own collaborators:

```java
class CheckoutService {
  private final PaymentGateway gateway = new RazorpayGateway("key", "secret");   // hard-wired
}
```

You can't test it without hitting Razorpay, can't swap the provider, and config leaks into
business code.

**With DI**, the class only *declares* what it needs, and someone else provides it:

```java
class CheckoutService {
  private final PaymentGateway gateway;
  CheckoutService(PaymentGateway gateway) { this.gateway = gateway; }   // ⭐ "give me one"
}
```

**Inversion of Control (IoC):** the *framework* creates objects and calls your code, instead of
your code creating everything. **DI** is the main technique. The **container**
(`ApplicationContext`) holds every **bean** (a managed object), resolves dependencies, and
manages lifecycles.

| NestJS | Spring | Notes |
|---|---|---|
| `@Injectable()` provider | `@Component` / `@Service` / `@Repository` | |
| `@Module({ providers: [...] })` | `@Configuration` classes + component scanning | Spring finds beans by **scanning packages**; no `providers` list |
| `{ provide: TOKEN, useClass: Impl }` | a `@Bean` method returning the interface type, or `@Primary`/`@Qualifier` | Spring keys beans by **type** (+ name) |
| `useFactory` | a `@Bean` method | |
| `useValue` | `@Bean` returning a constant | |
| `@Inject(TOKEN)` | `@Qualifier("name")` | |
| `@Optional()` | `ObjectProvider<T>` / `Optional<T>` | |
| scopes `DEFAULT` / `REQUEST` / `TRANSIENT` | `singleton` / `request` / `prototype` | the same three ideas |
| `onModuleInit` / `onModuleDestroy` | `@PostConstruct` / `@PreDestroy` | |
| `ConfigService.get('X')` | `@ConfigurationProperties` records | typed, not string keys |
| `forwardRef()` for circular deps | (none: cycles are rejected at startup) | Spring Boot forbids cycles by default |

---

## 2. Declaring beans ⭐⭐⭐

**1. Stereotype annotations + component scanning.** For *your own* classes:

```java
@Service class CheckoutService { CheckoutService(PaymentGateway gateway) { … } }
```

`@SpringBootApplication` on `ApiApplication` is three annotations in one:

- `@Configuration`;
- `@EnableAutoConfiguration` (§7);
- `@ComponentScan`, which scans `com.masternova.api` **and all sub-packages**.

Classes outside that package are not found.

| Annotation | Means | Extra behaviour |
|---|---|---|
| `@Component` | a generic bean | — |
| `@Service` | business logic | none: documents intent |
| `@Repository` | data access | translates persistence exceptions to Spring's `DataAccessException` |
| `@Controller` / `@RestController` | web endpoints | request mapping; `@RestController` = `@Controller` + `@ResponseBody` |
| `@Configuration` | holds `@Bean` methods | proxied by default (§7) |

**2. `@Bean` methods in a `@Configuration` class.** For *third-party* classes (you can't annotate
`Clock` or `SecurityFilterChain`) or when creation needs logic:

```java
@Configuration(proxyBeanMethods = false)
class PlatformConfig {
  @Bean Clock clock() { return Clock.systemUTC(); }          // real code, platform module
}
```

---

## 3. Injection styles: constructor wins ⭐⭐⭐

```java
// ✅ CONSTRUCTOR injection: our rule for every bean
@Service
class CheckoutService {
  private final PaymentGateway gateway;                          // final: immutable, thread-safe
  CheckoutService(PaymentGateway gateway) { this.gateway = gateway; }   // one constructor → no @Autowired needed
}

// ❌ FIELD injection
@Service
class CheckoutService {
  @Autowired private PaymentGateway gateway;                     // not final; hidden dependency
}
```

| Why constructor injection | |
|---|---|
| Dependencies are **visible** in the signature | a 7-parameter constructor screams "this class does too much" (SRP) |
| Fields are **`final`** | immutable after construction, safe to share across threads (note 07) |
| **Testable without Spring** | `new CheckoutService(new FakeGateway())` |
| **Fails fast** | a missing dependency stops startup, never a `NullPointerException` later |
| **Cycles become impossible** | `constructorInjectionMakesCyclesImpossibleToStart`: A(B) + B(A) → `BeanCurrentlyInCreationException`. A cycle is a design smell, so fix the design (extract a third class, or use an event). |

Setter injection is only for genuinely optional dependencies. Field injection: never in our code.

---

## 4. How Spring picks a bean ⭐⭐⭐

For a constructor parameter `Channel channel`, Spring looks for beans **of that type**:

| Candidates | Result | Test |
|---|---|---|
| exactly one | injected | `aSingleCandidateIsInjectedByType` |
| several, one is **`@Primary`** | the primary one | `primaryBreaksTheTie` |
| several, the parameter has **`@Qualifier("sms")`** | the bean named `sms` | `qualifierPicksByName` |
| several, the **parameter name** matches a bean name (`Channel sms`) | that bean (a fallback; needs `-parameters`, which Boot's parent POM sets) | |
| several, no tie-breaker | **startup fails**: `NoUniqueBeanDefinitionException: expected single matching bean but found 2: email,sms` | `twoCandidatesAndNoTieBreakerFailsAtStartup` |
| none | **startup fails**: `NoSuchBeanDefinitionException` | |

**Ask for all of them, or none:**

```java
List<Channel> channels            // every Channel bean, sorted by @Order  ← how the Strategy registry gets its strategies
Map<String, Channel> byBeanName   // bean name → bean
ObjectProvider<Channel> channel   // lazy / optional: getIfAvailable(), getObject(), stream()
Optional<Channel> channel         // optional
```

`listAndMapInjectionCollectEveryCandidate` and `objectProviderMakesADependencyOptional` show
both. The real `MetaController` uses `ObjectProvider<BuildProperties>`, because build info only
exists in a Maven-built jar.

---

## 5. Scopes, and the prototype-in-singleton trap ⭐⭐⭐

| Scope | Instances | Use |
|---|---|---|
| **`singleton`** (default) | **one per container**, shared by every thread | services, repositories, controllers: **almost everything** |
| `prototype` | a new one per injection / `getBean` | stateful helpers (rare) |
| `request` | one per HTTP request | request-scoped context (rare; prefer passing values) |
| `session` | one per HTTP session | we're stateless, so not used |

⭐ **Singleton means shared by all request threads.** It must be stateless or thread-safe (note
07 §1).

**The trap:** `aPrototypeInjectedIntoASingletonIsCreatedOnlyOnce`

```java
record CheckoutService(Cart injectedOnce, ObjectProvider<Cart> carts) {}
// The singleton is built ONCE, so `injectedOnce` is the same Cart forever: "prototype" did nothing.
// carts.getObject() asks the container each time → a genuinely new Cart.
```

Fixes: inject `ObjectProvider<T>` (the simplest), use an `@Lookup` method, or use a scoped proxy
(`@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)`).

---

## 6. The bean lifecycle ⭐⭐

```text
instantiate (constructor + constructor injection)
  → setter/field injection
  → BeanPostProcessor "before init"
  → @PostConstruct  (or InitializingBean.afterPropertiesSet, or @Bean(initMethod))
  → BeanPostProcessor "after init"    ← ⭐ PROXIES are created here (@Transactional, @Async, @Cacheable: task 1.9)
  → READY: in use
  …
context.close()  → @PreDestroy (or DisposableBean, or @Bean(destroyMethod))
```

`dependenciesStartFirstAndStopLast` records the real order:

```text
database: constructor → database: @PostConstruct → repository: constructor → repository: @PostConstruct
… repository: @PreDestroy → database: @PreDestroy          ← shutdown in REVERSE dependency order
```

A dependency is **fully initialised before** it's injected, and **destroyed after** everything
that uses it.

**Where to put startup work:**

| Need | Use |
|---|---|
| initialise this bean's own state | constructor, or `@PostConstruct` |
| run once the whole app is up (warm caches, log config) | `ApplicationRunner` / `@EventListener(ApplicationReadyEvent.class)` |
| start and stop background work with the app | `SmartLifecycle` |
| clean up (flush buffers: Phase 10's progress buffer) | `@PreDestroy` + graceful shutdown (`server.shutdown: graceful`, already set) |

---

## 7. `@Configuration`, profiles, conditions, auto-configuration ⭐⭐⭐

### Full vs lite `@Configuration` (classic interview question)

```java
@Configuration                                   // proxyBeanMethods = true (default)
class Full {
  @Bean ConnectionPool pool() { return new ConnectionPool(); }
  @Bean ServiceA serviceA() { return new ServiceA(pool()); }   // ⭐ the CGLIB proxy intercepts pool() → the singleton
}

@Configuration(proxyBeanMethods = false)          // "lite": no proxy, faster startup
class Lite {
  @Bean ServiceA serviceA() { return new ServiceA(pool()); }   // ⚠️ a plain Java call → a SECOND pool
  @Bean ServiceB serviceB(ConnectionPool pool) { … }           // ✅ take it as a parameter instead
}
```

`fullConfigurationReturnsTheSameBeanFromInterBeanCalls` and
`liteConfigurationCallsAreJustJavaCalls` prove both. **Our convention:**
`proxyBeanMethods = false`, with dependencies always taken as `@Bean` method parameters. This is
explicit, has no proxy, and starts faster (Spring Boot's own configs do the same).

> 🐛 **Found while writing the test:** the first version used `Clock.systemUTC()` as the bean,
> and the "lite creates a second instance" assertion failed, because `Clock.systemUTC()` returns
> a **cached constant**. Two calls, same object. Experiments need values that are really
> created fresh.

### Profiles: per-environment beans

```java
@Bean @Profile("dev")  MailSender devMail()  { return new LoggingMailSender(); }   // spring.profiles.active=dev
@Bean @Profile("!dev") MailSender realMail() { return new SmtpMailSender(); }
```

`application-dev.yaml` holds dev-only settings and overrides `application.yaml` when the profile
is active. Keep profiles few (`dev`, `test`, `prod`), and put environment differences in
**properties**, not code.

### Conditions: beans that exist only if…

| Annotation | Bean created only if… |
|---|---|
| `@ConditionalOnProperty(name = "masternova.features.coupons", havingValue = "true")` | a property is set: **feature flags** (`propertyConditionsActAsFeatureFlags`) |
| `@ConditionalOnMissingBean` | the app hasn't defined its own: the **back-off** rule |
| `@ConditionalOnClass(X.class)` | a library is on the classpath |
| `@ConditionalOnBean` / `@ConditionalOnWebApplication` / … | |

### Auto-configuration: the "magic" explained

Spring Boot ships hundreds of `@AutoConfiguration` classes, listed in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Each one is
guarded by conditions. Add `spring-boot-starter-data-redis`, and the Redis auto-config sees
Redis on the classpath and creates a `RedisConnectionFactory`, **unless you defined one**
(`@ConditionalOnMissingBean`).

`autoConfigurationProvidesADefaultThatYourOwnBeanReplaces` builds a mini auto-config: a default
`Clock` that disappears as soon as the user config defines `fixedClock`. That's how *every*
Boot default works.

**Debugging it:** start with `--debug` (prints the condition evaluation report: what matched and
why), or call `GET /actuator/conditions`.

---

## 8. Configuration properties ⭐⭐⭐

**Real code:** [`MasternovaProperties`](../../backend/api/src/main/java/com/masternova/api/platform/MasternovaProperties.java)

```java
@Validated
@ConfigurationProperties(prefix = "masternova")
public record MasternovaProperties(
    @NotNull URI webUrl,
    @Valid @DefaultValue Checkout checkout) {                                // ⭐ empty @DefaultValue: see below
  public record Checkout(
      @DefaultValue("20") @Min(1) @Max(100) int cartMaxItems,
      @DefaultValue("10s") @NotNull Duration paymentTimeout) {}
}
```

```yaml
masternova:
  web-url: ${WEB_URL:http://localhost:4200}
  checkout:
    cart-max-items: 20
    payment-timeout: 10s
```

It's registered by `@ConfigurationPropertiesScan` on `ApiApplication`, and injected like any
bean: `CheckoutService(MasternovaProperties props)`.

| `@ConfigurationProperties` record ✅ | `@Value("${masternova.checkout.cart-max-items}")` ❌ (for anything non-trivial) |
|---|---|
| typed: `Duration`, `URI`, `int`, nested records | strings and SpEL scattered across classes |
| **validated at startup** (`anInvalidValueFailsStartup`: `cart-max-items: 0` stops the app) | a typo means a runtime failure, or a silent default |
| one place to read all settings | hunt through the code |
| IDE autocompletion via `spring-boot-configuration-processor` (in our POM) | none |

**Binding features** (`PropertiesBindingLearningTest`):

- **Relaxed binding:**
  - `cart-max-items`, `cartMaxItems` and `CART_MAX_ITEMS` all bind to `cartMaxItems`.
  - In env vars, `MASTERNOVA_CHECKOUT_CARTMAXITEMS` overrides the yaml. That's how containers and
    Kubernetes configure the app (Phase D4).
- **Durations:** `10s`, `1500ms`, `2m`, `PT10S` all work.
- **`@DefaultValue`** on record components.

> 🐛 **Found while writing the test:** with no `masternova.checkout.*` keys at all, the nested
> record bound as **`null`**, and `@NotNull` failed startup. An **empty `@DefaultValue`** on the
> nested parameter tells Boot to build it from its own defaults.

**Where values come from** (later sources override earlier ones):

1. `@DefaultValue` in code
2. `application.yaml`
3. `application-{profile}.yaml`
4. OS **environment variables** (e.g. `DATABASE_URL`)
5. Java system properties (`-Dmasternova.web-url=…`)
6. command-line arguments (`--masternova.web-url=…`)

**Secrets** (DB password, Razorpay keys, JWT secret) **never go in the yaml in git**. They come
from env vars, as our `application.yaml` does with `${DATABASE_PASSWORD:...}`, or from a secret
store (Kubernetes Secrets in D4, AWS SSM in D6).

---

## 9. Walkthrough: the learning tests ⭐⭐

| Test class | Proves |
|---|---|
| [`DependencyInjectionLearningTest`](../../backend/api/src/test/java/com/masternova/api/learning/ioc/DependencyInjectionLearningTest.java) | injection by type; ambiguity fails at startup; `@Primary`; `@Qualifier`; `List`/`Map` injection with `@Order`; `ObjectProvider`; constructor cycles rejected |
| [`ScopesAndLifecycleLearningTest`](../../backend/api/src/test/java/com/masternova/api/learning/ioc/ScopesAndLifecycleLearningTest.java) | singleton vs prototype; the prototype-in-singleton trap and the `ObjectProvider` fix; lifecycle order including reverse shutdown |
| [`ConfigurationAndConditionsLearningTest`](../../backend/api/src/test/java/com/masternova/api/learning/ioc/ConfigurationAndConditionsLearningTest.java) | full vs lite `@Configuration`; `@Profile`; `@ConditionalOnProperty` as a feature flag; auto-configuration back-off with `@ConditionalOnMissingBean` |
| [`PropertiesBindingLearningTest`](../../backend/api/src/test/java/com/masternova/api/learning/ioc/PropertiesBindingLearningTest.java) | binding the real `MasternovaProperties`: defaults, relaxed names, durations, validation failing startup |

**`ApplicationContextRunner` cheat sheet:**

```java
new ApplicationContextRunner()
    .withUserConfiguration(MyConfig.class)                          // your @Configuration classes
    .withConfiguration(AutoConfigurations.of(SomeAutoConfig.class)) // auto-configs (processed after user config)
    .withBean(EventLog.class, () -> log)                            // a single bean
    .withPropertyValues("spring.profiles.active=dev", "x.y=z")      // properties / profiles
    .run(ctx -> {
      assertThat(ctx).hasSingleBean(Clock.class);                   // AssertJ for contexts
      assertThat(ctx).hasFailed();
      assertThat(ctx.getStartupFailure()).rootCause().isInstanceOf(...);
    });
```

---

## 10. How Masternova uses this, and the rules for new modules ⭐⭐⭐

**Already in the code:**

| Class | DI feature |
|---|---|
| `ApiApplication` | `@SpringBootApplication` (scan + auto-config) + `@ConfigurationPropertiesScan` |
| `PlatformConfig` | a `@Bean Clock` (lite `@Configuration`), so time is injectable and tests use a fixed clock |
| `SecurityConfig` | a `@Bean SecurityFilterChain`, built from the injected `HttpSecurity` |
| `MetaController` | constructor injection of `Clock` + `ObjectProvider<BuildProperties>` (optional) |
| `GlobalExceptionHandler` | `@RestControllerAdvice`: a bean Spring MVC discovers by annotation |
| `MasternovaProperties` | typed, validated config |
| Spring Boot itself | auto-configured `DataSource`, `RedisConnectionFactory`, `ObjectMapper`, `SecurityFilterChain` defaults… all backing off where we define our own |

**Rules for every module from Phase 2 on** (also in `CLAUDE.md` §3/§6):

1. **Constructor injection only**, `final` fields, no `@Autowired` on fields.
2. **Depend on interfaces at seams** (gateways, storage, mail). Concrete classes elsewhere (YAGNI).
3. **Package-private by default.** Only the module's public API is `public` (Spring injects
   package-private classes just fine).
4. **`@Configuration(proxyBeanMethods = false)`**, with dependencies as `@Bean` parameters.
5. **Settings live in `MasternovaProperties`** (or a module's own properties record). No
   scattered `@Value`.
6. **Beans are stateless singletons** (note 07). Request data stays in parameters.
7. **No cycles.** If two modules need each other, publish an event (Phase 2) or extract a third
   class.

---

## 11. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | § |
|---|---|---|
| `@Autowired` on fields | constructor injection | 3 |
| `new SomeService()` inside a bean | inject it | 1 |
| two implementations, no tie-breaker | `@Primary` / `@Qualifier`, or inject `List`/`Map` | 4 |
| expecting a prototype injected into a singleton to refresh | `ObjectProvider<T>` | 5 |
| mutable fields in a singleton | stateless beans | 5 |
| calling a `@Bean` method directly in a lite config | take it as a `@Bean` parameter | 7 |
| a bean class outside the scanned package | keep it under `com.masternova.api` | 2 |
| `@Value` strings everywhere | a `@ConfigurationProperties` record with validation | 8 |
| a nested properties record with no keys binds as null | an empty `@DefaultValue` on the nested parameter | 8 |
| secrets in `application.yaml` | env vars / a secret store | 8 |
| "solving" a circular dependency with `@Lazy` or setter injection | redesign: events or a third class | 3 |
| heavy work in a constructor (network calls) | `ApplicationRunner` / a lifecycle method | 6 |
| fighting auto-config with exclusions everywhere | define your own bean; auto-config backs off | 7 |

---

## 12. Interview Q&A ⭐⭐⭐

**Q1. What are IoC and DI?**
IoC: the framework creates objects and calls your code, instead of the other way round. DI: an
object receives its dependencies (constructor/setter) instead of creating them. The Spring
container manages beans, their wiring and their lifecycle.

**Q2. Constructor vs setter vs field injection? Which and why?**
Constructor: final fields, visible dependencies, testable without Spring, fails fast, cycles
detected. Setter: for optional dependencies only. Field: avoid (hidden, not final, needs
reflection to test).

**Q3. Two beans of the same type. What happens, and how do you fix it?**
`NoUniqueBeanDefinitionException` at startup. Fix it with `@Primary`, `@Qualifier`, a parameter
name matching the bean name, or by injecting `List`/`Map` of all of them.

**Q4. Bean scopes? Is a singleton bean thread-safe?**
singleton (the default), prototype, request, session, application. A singleton is shared by all
threads, and it's thread-safe only if it's stateless or its state is thread-safe.

**Q5. What happens when you inject a prototype into a singleton?**
It's created once, at singleton creation, so it never refreshes. Use `ObjectProvider`,
`@Lookup`, or a scoped proxy.

**Q6. Describe the bean lifecycle.**
Instantiate → inject → BeanPostProcessor before → `@PostConstruct` → BeanPostProcessor after
(proxies created here) → in use → `@PreDestroy` on shutdown, in reverse dependency order.

**Q7. `@Component` vs `@Bean`?**
`@Component` goes on your class and is found by scanning. `@Bean` is a factory method in a
`@Configuration` class, used for third-party classes or creation logic.

**Q8. What does `proxyBeanMethods` do?**
`true` (full): a CGLIB proxy makes inter-`@Bean` method calls return the singleton. `false`
(lite): plain Java calls. Faster, so pass dependencies as parameters instead.

**Q9. How does Spring Boot auto-configuration work?**
`@AutoConfiguration` classes listed in an imports file, each guarded by `@Conditional…`
annotations (classpath, properties, missing beans). Your own beans win via
`@ConditionalOnMissingBean`. Debug with `--debug` or `/actuator/conditions`.

**Q10. `@ConfigurationProperties` vs `@Value`?**
The first is typed, grouped, validated at startup, relaxed binding, IDE metadata. The second is
a single string expression, scattered, and validated late. Use `@ConfigurationProperties` for
anything beyond one trivial value.

**Q11. How does Spring handle circular dependencies?**
With constructor injection, they fail at startup (`BeanCurrentlyInCreationException`). Spring
Boot also forbids field/setter cycles by default. Fix the design.

---

## 13. 30-second recall

- **IoC/DI:** declare beans and their constructor needs; the container creates, wires and
  manages them. It's NestJS providers with type-based lookup.
- **Declaring beans:** `@Component`/`@Service`/`@Repository`/`@RestController` (found by
  scanning under the app package), and `@Bean` methods for third-party classes and factories.
- **Injection:** constructor only, `final` fields. Cycles fail at startup, and that's a good
  thing.
- **Resolution:**
  - By type. Ties go to `@Primary`, then `@Qualifier`, then the parameter name.
  - Otherwise `NoUniqueBeanDefinitionException` at startup.
  - `List`/`Map`/`ObjectProvider` give you all of them or an optional one.
- **Scopes:** singleton (default, shared by all threads, so keep it stateless). A prototype
  injected into a singleton is frozen; use `ObjectProvider`.
- **Lifecycle:** constructor → `@PostConstruct` → (proxies) → use → `@PreDestroy`, in reverse
  dependency order.
- **`@Configuration`:** full = CGLIB proxy (inter-bean calls return the singleton); lite = plain
  calls, so pass parameters. We use lite.
- **Conditions and auto-config:** `@Profile`, `@ConditionalOnProperty` (feature flags),
  `@ConditionalOnMissingBean` (back-off). Debug with `--debug`.
- **Config:**
  - `@ConfigurationProperties` records: typed, validated at startup, relaxed binding, env
    overrides.
  - Use an empty `@DefaultValue` for nested records.
  - Secrets come from the environment.
- **Next:** [09 — Spring AOP & proxies](README.md) (task 1.9).
