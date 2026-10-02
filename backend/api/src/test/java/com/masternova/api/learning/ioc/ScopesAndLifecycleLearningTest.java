package com.masternova.api.learning.ioc;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/** Bean scopes and the bean lifecycle. Study note §5–§6. */
class ScopesAndLifecycleLearningTest {

  /** A bean with an identity, so tests can tell instances apart. */
  static final class Cart {
    final String id = UUID.randomUUID().toString();
  }

  /** A singleton that (wrongly) holds a prototype directly, and (rightly) through a provider. */
  record CheckoutService(Cart injectedOnce, ObjectProvider<Cart> carts) {}

  @Configuration(proxyBeanMethods = false)
  static class Scopes {
    @Bean // singleton: the default scope
    Object catalog() {
      return new Object();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // ⭐ a NEW instance per lookup/injection
    Cart cart() {
      return new Cart();
    }

    @Bean
    CheckoutService checkoutService(Cart cart, ObjectProvider<Cart> carts) {
      return new CheckoutService(cart, carts);
    }
  }

  @Test
  void singletonIsSharedPrototypeIsFreshEachTime() {
    new ApplicationContextRunner()
        .withUserConfiguration(Scopes.class)
        .run(
            ctx -> {
              assertThat(ctx.getBean("catalog")).isSameAs(ctx.getBean("catalog"));
              assertThat(ctx.getBean(Cart.class).id).isNotEqualTo(ctx.getBean(Cart.class).id);
            });
  }

  @Test
  void aPrototypeInjectedIntoASingletonIsCreatedOnlyOnce() {
    new ApplicationContextRunner()
        .withUserConfiguration(Scopes.class)
        .run(
            ctx -> {
              CheckoutService service = ctx.getBean(CheckoutService.class);

              // ⚠️ THE TRAP: the singleton was built once, so its injected "prototype" is frozen.
              assertThat(service.injectedOnce())
                  .isSameAs(ctx.getBean(CheckoutService.class).injectedOnce());
              // ✅ An ObjectProvider asks the container each time → genuinely fresh instances.
              assertThat(service.carts().getObject().id)
                  .isNotEqualTo(service.carts().getObject().id);
            });
  }

  // ---- lifecycle
  static final class EventLog {
    final List<String> events = new ArrayList<>();
  }

  static final class Database {
    private final EventLog log;

    Database(EventLog log) {
      this.log = log;
      log.events.add("database: constructor");
    }

    @PostConstruct
    void connect() {
      log.events.add("database: @PostConstruct");
    }

    @PreDestroy
    void disconnect() {
      log.events.add("database: @PreDestroy");
    }
  }

  static final class Repository {
    private final EventLog log;

    Repository(Database db, EventLog log) { // depends on Database
      this.log = log;
      log.events.add("repository: constructor");
    }

    @PostConstruct
    void warmUp() {
      log.events.add("repository: @PostConstruct");
    }

    @PreDestroy
    void flush() {
      log.events.add("repository: @PreDestroy");
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Lifecycle {
    @Bean
    Database database(EventLog log) {
      return new Database(log);
    }

    @Bean
    Repository repository(Database db, EventLog log) {
      return new Repository(db, log);
    }
  }

  @Test
  void dependenciesStartFirstAndStopLast() {
    EventLog log = new EventLog();

    new ApplicationContextRunner()
        .withBean(EventLog.class, () -> log)
        .withUserConfiguration(Lifecycle.class)
        .run(ctx -> assertThat(ctx).hasNotFailed()); // the context is CLOSED after run()

    assertThat(log.events)
        .containsExactly(
            "database: constructor", //        a dependency is created first …
            "database: @PostConstruct", //     … and fully initialised before it's injected
            "repository: constructor",
            "repository: @PostConstruct",
            "repository: @PreDestroy", //      shutdown runs in REVERSE: dependents first,
            "database: @PreDestroy"); //       so the database outlives everything that uses it
  }
}
