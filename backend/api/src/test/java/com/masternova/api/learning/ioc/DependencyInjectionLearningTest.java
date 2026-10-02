package com.masternova.api.learning.ioc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

/**
 * How Spring decides WHICH bean to inject. Each test starts a tiny ApplicationContext (no web, no
 * DB) with ApplicationContextRunner — the fastest way to learn and test Spring wiring.
 *
 * <p>Study note: {@code patterns/java/08-spring-ioc-and-di.md} §3–§4.
 */
class DependencyInjectionLearningTest {

  interface Channel {
    String name();
  }

  record EmailChannel() implements Channel {
    public String name() {
      return "email";
    }
  }

  record SmsChannel() implements Channel {
    public String name() {
      return "sms";
    }
  }

  /** Needs exactly ONE Channel, through its constructor. */
  record Notifier(Channel channel) {}

  /** Needs ALL Channels. */
  record Broadcaster(List<Channel> channels, Map<String, Channel> byBeanName) {}

  @Configuration(proxyBeanMethods = false)
  static class OneChannel {
    @Bean
    Channel email() {
      return new EmailChannel();
    }

    @Bean
    Notifier notifier(
        Channel channel) { // ⭐ the parameter IS the dependency (constructor injection)
      return new Notifier(channel);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class TwoChannels {
    @Bean
    Channel email() {
      return new EmailChannel();
    }

    @Bean
    Channel sms() {
      return new SmsChannel();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class AmbiguousNotifier {
    @Bean
    Notifier notifier(Channel channel) { // two candidates, neither named "channel" → ambiguous
      return new Notifier(channel);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PrimaryChannels {
    @Bean
    @Primary // ⭐ "the default when several match"
    Channel email() {
      return new EmailChannel();
    }

    @Bean
    Channel sms() {
      return new SmsChannel();
    }

    @Bean
    Notifier notifier(Channel channel) {
      return new Notifier(channel);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class QualifiedNotifier {
    @Bean
    Notifier notifier(@Qualifier("sms") Channel channel) { // ⭐ pick one by bean name
      return new Notifier(channel);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OrderedChannels {
    @Bean
    @Order(2)
    Channel email() {
      return new EmailChannel();
    }

    @Bean
    @Order(1)
    Channel sms() {
      return new SmsChannel();
    }

    @Bean
    Broadcaster broadcaster(List<Channel> channels, Map<String, Channel> byBeanName) {
      return new Broadcaster(channels, byBeanName); // ⭐ ALL beans of the type
    }
  }

  @Test
  void aSingleCandidateIsInjectedByType() {
    new ApplicationContextRunner()
        .withUserConfiguration(OneChannel.class)
        .run(ctx -> assertThat(ctx.getBean(Notifier.class).channel().name()).isEqualTo("email"));
  }

  @Test
  void twoCandidatesAndNoTieBreakerFailsAtStartup() {
    new ApplicationContextRunner()
        .withUserConfiguration(TwoChannels.class, AmbiguousNotifier.class)
        .run(
            ctx -> {
              assertThat(ctx)
                  .hasFailed(); // ⭐ wiring errors fail FAST — at startup, not on first use
              assertThat(ctx.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(NoUniqueBeanDefinitionException.class);
            });
  }

  @Test
  void primaryBreaksTheTie() {
    new ApplicationContextRunner()
        .withUserConfiguration(PrimaryChannels.class)
        .run(ctx -> assertThat(ctx.getBean(Notifier.class).channel().name()).isEqualTo("email"));
  }

  @Test
  void qualifierPicksByName() {
    new ApplicationContextRunner()
        .withUserConfiguration(TwoChannels.class, QualifiedNotifier.class)
        .run(ctx -> assertThat(ctx.getBean(Notifier.class).channel().name()).isEqualTo("sms"));
  }

  @Test
  void listAndMapInjectionCollectEveryCandidate() {
    new ApplicationContextRunner()
        .withUserConfiguration(OrderedChannels.class)
        .run(
            ctx -> {
              Broadcaster broadcaster = ctx.getBean(Broadcaster.class);
              // List: sorted by @Order. Map: bean name → bean. (The Strategy registry, for free.)
              assertThat(broadcaster.channels())
                  .extracting(Channel::name)
                  .containsExactly("sms", "email");
              assertThat(broadcaster.byBeanName()).containsOnlyKeys("email", "sms");
            });
  }

  @Test
  void objectProviderMakesADependencyOptional() {
    new ApplicationContextRunner()
        .run(
            ctx -> {
              ObjectProvider<Channel> channel = ctx.getBeanProvider(Channel.class);
              assertThat(channel.getIfAvailable()).isNull(); // no bean, no failure
              assertThat(channel.getIfAvailable(EmailChannel::new).name()).isEqualTo("email");
            });
  }

  // ---- circular dependency: A needs B, B needs A
  record ServiceA(ServiceB b) {}

  record ServiceB(ServiceA a) {}

  @Configuration(proxyBeanMethods = false)
  static class Circular {
    @Bean
    ServiceA serviceA(ServiceB b) {
      return new ServiceA(b);
    }

    @Bean
    ServiceB serviceB(ServiceA a) {
      return new ServiceB(a);
    }
  }

  @Test
  void constructorInjectionMakesCyclesImpossibleToStart() {
    new ApplicationContextRunner()
        .withUserConfiguration(Circular.class)
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              // ⭐ A cycle is a DESIGN smell (two classes that can't exist without each other).
              //    Constructor injection surfaces it immediately instead of hiding it.
              assertThat(ctx.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(BeanCurrentlyInCreationException.class);
            });
  }
}
