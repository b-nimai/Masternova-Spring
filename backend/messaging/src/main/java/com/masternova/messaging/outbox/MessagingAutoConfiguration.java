package com.masternova.messaging.outbox;

import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxProperties;
import com.masternova.messaging.OutboxWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⭐ How a Spring Boot "starter" works: this class is listed in {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so ANY app
 * with this jar on its classpath gets these beans — no component scan of {@code
 * com.masternova.messaging}, no {@code @Import} in the app. Conditions decide which beans appear.
 *
 * <ul>
 *   <li>always: the {@link OutboxWriter} (and the repository behind it) — every app may publish;
 *   <li>only with {@code masternova.outbox.relay-enabled=true}: the relay and its schedule —
 *       exactly one deployable consumes (the worker; ADR-0008).
 * </ul>
 *
 * Auto-configurations run AFTER the app's own configuration, which is what makes {@code
 * ConditionalOnMissingBean} a safe default: the api's own {@code Clock} wins over the fallback.
 *
 * <p>Meters go to the app's {@link MeterRegistry} (Prometheus, with actuator) or, in an app without
 * one, to Micrometer's global registry, which drops them: metrics are never required to run.
 */
@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
public class MessagingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock messagingClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  OutboxRepository outboxRepository(JdbcClient jdbc, Clock clock, JsonMapper json) {
    return new JdbcOutboxRepository(jdbc, clock, json);
  }

  @Bean
  @ConditionalOnMissingBean
  OutboxWriter outboxWriter(
      OutboxRepository repository,
      JsonMapper json,
      Clock clock,
      ObjectProvider<MeterRegistry> meters,
      ObjectProvider<ObservationRegistry> observations) {
    return new JdbcOutboxWriter(
        repository, json, clock, registry(meters), observations(observations));
  }

  /**
   * Backlog gauges in EVERY app that writes the outbox — not only with the relay: a worker whose
   * relay is switched off is exactly the stall they must show. Alerts take the max across apps.
   */
  @Bean
  @ConditionalOnMissingBean
  OutboxBacklogMetrics outboxBacklogMetrics(OutboxRepository repository, Clock clock) {
    return new OutboxBacklogMetrics(repository, clock);
  }

  static MeterRegistry registry(ObjectProvider<MeterRegistry> meters) {
    return meters.getIfAvailable(() -> Metrics.globalRegistry);
  }

  /** No registry (no actuator): observations are no-ops and the headers stay empty. */
  static ObservationRegistry observations(ObjectProvider<ObservationRegistry> observations) {
    return observations.getIfAvailable(() -> ObservationRegistry.NOOP);
  }

  /** The consuming side. Nested so ONE property switches the relay and its schedule together. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBooleanProperty("masternova.outbox.relay-enabled")
  @EnableScheduling
  static class RelayConfiguration {

    @Bean
    OutboxRelay outboxRelay(
        OutboxRepository repository,
        List<OutboxHandler> handlers,
        OutboxProperties settings,
        Clock clock,
        ObjectProvider<MeterRegistry> meters,
        ObjectProvider<ObservationRegistry> observations) {
      return new OutboxRelay(
          repository, handlers, settings, clock, registry(meters), observations(observations));
    }

    @Bean
    OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
      return new OutboxRelayScheduler(relay);
    }
  }
}
