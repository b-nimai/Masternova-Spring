package com.masternova.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.messaging.OutboxWriter;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⭐ Auto-configuration conditions, proven without a database: {@code ApplicationContextRunner}
 * starts a tiny context per assertion with exactly the beans and properties given.
 */
class MessagingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
          .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
          .withBean(JsonMapper.class, JsonMapper::new);

  static OutboxHandler handlerFor(String type) {
    return new OutboxHandler() {
      @Override
      public String eventType() {
        return type;
      }

      @Override
      public void handle(OutboxMessage message) {}
    };
  }

  @Test
  void everyAppCanWriteButNobodyRelaysByDefault() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(OutboxWriter.class);
          assertThat(context).doesNotHaveBean(OutboxRelay.class); // ⭐ the api's situation
          assertThat(context).doesNotHaveBean(OutboxRelayScheduler.class);
        });
  }

  @Test
  void relayEnabledAddsTheRelayAndItsSchedule() {
    runner
        .withPropertyValues("masternova.outbox.relay-enabled=true") // the worker's situation
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxRelay.class);
              assertThat(context).hasSingleBean(OutboxRelayScheduler.class);
            });
  }

  @Test
  void theAppsOwnClockWinsOverTheFallback() {
    Clock fixed = Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC);
    runner
        .withBean(Clock.class, () -> fixed)
        .run(context -> assertThat(context.getBean(Clock.class)).isSameAs(fixed));
  }

  @Test
  void twoHandlersForOneEventTypeFailStartup() {
    runner
        .withPropertyValues("masternova.outbox.relay-enabled=true")
        .withBean("first", OutboxHandler.class, () -> handlerFor("x.happened.v1"))
        .withBean("second", OutboxHandler.class, () -> handlerFor("x.happened.v1"))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("two OutboxHandlers for x.happened.v1"));
  }

  @Test
  void invalidSettingsFailStartup() {
    runner
        .withPropertyValues("masternova.outbox.batch-size=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
