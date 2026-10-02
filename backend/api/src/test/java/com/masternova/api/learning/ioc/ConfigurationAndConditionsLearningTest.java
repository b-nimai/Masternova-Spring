package com.masternova.api.learning.ioc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @Configuration proxying, profiles, conditions and auto-configuration. Study note §7.
 */
class ConfigurationAndConditionsLearningTest {

  /**
   * Created with `new` every time (careful: Clock.systemUTC() would NOT do — it's a cached
   * constant).
   */
  static final class ConnectionPool {}

  record ServiceA(ConnectionPool pool) {}

  record ServiceB(ConnectionPool pool) {}

  @Configuration // proxyBeanMethods = true (the default): Spring subclasses this class (CGLIB)
  static class FullConfiguration {
    @Bean
    ConnectionPool pool() {
      return new ConnectionPool();
    }

    @Bean
    ServiceA serviceA() {
      return new ServiceA(pool()); // ⭐ intercepted by the proxy → returns the SINGLETON bean
    }

    @Bean
    ServiceB serviceB() {
      return new ServiceB(pool());
    }
  }

  @Configuration(proxyBeanMethods = false) // "lite" mode: no proxy, faster startup
  static class LiteConfiguration {
    @Bean
    ConnectionPool pool() {
      return new ConnectionPool();
    }

    @Bean
    ServiceA serviceA() {
      return new ServiceA(pool()); // ⚠️ a plain Java call → a SECOND pool, not the bean
    }

    @Bean
    ServiceB serviceB(ConnectionPool pool) { // ✅ the lite-mode way: take it as a parameter
      return new ServiceB(pool);
    }
  }

  @Test
  void fullConfigurationReturnsTheSameBeanFromInterBeanCalls() {
    new ApplicationContextRunner()
        .withUserConfiguration(FullConfiguration.class)
        .run(
            ctx ->
                assertThat(ctx.getBean(ServiceA.class).pool())
                    .isSameAs(ctx.getBean(ServiceB.class).pool())
                    .isSameAs(ctx.getBean(ConnectionPool.class)));
  }

  @Test
  void liteConfigurationCallsAreJustJavaCalls() {
    new ApplicationContextRunner()
        .withUserConfiguration(LiteConfiguration.class)
        .run(
            ctx -> {
              ConnectionPool bean = ctx.getBean(ConnectionPool.class);
              assertThat(ctx.getBean(ServiceA.class).pool())
                  .isNotSameAs(bean); // a stray second pool
              assertThat(ctx.getBean(ServiceB.class).pool()).isSameAs(bean); //    injected: correct
            });
  }

  // ---- profiles and property conditions
  interface MailSender {}

  record SmtpMailSender() implements MailSender {}

  record LoggingMailSender() implements MailSender {}

  @Configuration(proxyBeanMethods = false)
  static class MailConfiguration {
    @Bean
    @Profile("dev") // ⭐ only when the "dev" profile is active
    MailSender devMail() {
      return new LoggingMailSender();
    }

    @Bean
    @Profile("!dev") // everywhere else
    MailSender realMail() {
      return new SmtpMailSender();
    }
  }

  record CouponEngine() {}

  @Configuration(proxyBeanMethods = false)
  static class FeatureFlags {
    @Bean
    @ConditionalOnProperty(name = "masternova.features.coupons", havingValue = "true")
    CouponEngine couponEngine() { // ⭐ a feature flag: the bean exists only when switched on
      return new CouponEngine();
    }
  }

  @Test
  void profilesSwapImplementations() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(MailConfiguration.class);

    runner
        .withPropertyValues("spring.profiles.active=dev")
        .run(
            ctx -> assertThat(ctx).getBean(MailSender.class).isInstanceOf(LoggingMailSender.class));
    runner.run(ctx -> assertThat(ctx).getBean(MailSender.class).isInstanceOf(SmtpMailSender.class));
  }

  @Test
  void propertyConditionsActAsFeatureFlags() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(FeatureFlags.class);

    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CouponEngine.class));
    runner
        .withPropertyValues("masternova.features.coupons=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(CouponEngine.class));
  }

  // ---- how Spring Boot auto-configuration "backs off"
  @AutoConfiguration
  static class DefaultClockAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean // ⭐ "only if the application didn't define its own"
    Clock defaultClock() {
      return Clock.systemUTC();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class FixedClockForTests {
    @Bean
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Test
  void autoConfigurationProvidesADefaultThatYourOwnBeanReplaces() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DefaultClockAutoConfiguration.class));

    runner.run(ctx -> assertThat(ctx).hasSingleBean(Clock.class).hasBean("defaultClock"));
    runner
        .withUserConfiguration(FixedClockForTests.class)
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(Clock.class)
                    .hasBean("fixedClock")
                    .doesNotHaveBean("defaultClock"));
  }
}
