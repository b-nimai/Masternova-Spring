package com.masternova.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.messaging.outbox.OutboxRelay;
import com.masternova.worker.MutableClock;
import com.masternova.worker.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

/**
 * ⭐ The whole send side, for real: an outbox row (as the api writes it) → the REAL relay → handler
 * → pipeline → SMTP → Mailpit, read back over Mailpit's API. Covers the failures unit tests can
 * only simulate: a redelivered event, and a worker that died mid-send.
 *
 * <p>The scheduler is effectively off ({@code poll-interval=1h} is also its initial delay), so each
 * test drives {@link OutboxRelay#relayOnce()} itself, against a {@link MutableClock}.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "masternova.outbox.relay-enabled=true",
      "masternova.outbox.poll-interval=1h",
      "masternova.outbox.lease=60s",
      "masternova.notification.web-url=http://localhost:8081"
    })
@Import({TestcontainersConfiguration.class, NotificationPipelineIT.Config.class})
class NotificationPipelineIT {

  @Container
  static final GenericContainer<?> mailpit =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
          .withExposedPorts(1025, 8025);

  @DynamicPropertySource
  static void smtp(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", mailpit::getHost);
    registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired OutboxRelay relay;
  @Autowired EmailDeliveries deliveries;
  @Autowired JdbcClient jdbc;
  @Autowired MutableClock clock;

  private RestClient mailpitApi;

  @BeforeEach
  void clean() {
    clock.reset();
    jdbc.sql("DELETE FROM outbox_message").update();
    jdbc.sql("DELETE FROM email_delivery").update();
    jdbc.sql("DELETE FROM email_suppression").update();
    jdbc.sql("DELETE FROM app_user").update(); // cascades to notification_preference
    mailpitApi =
        RestClient.create(
            "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1");
    mailpitApi.delete().uri("/messages").retrieve().toBodilessEntity();
  }

  // ---------------------------------------------------------------------------------- helpers

  /** Exactly what the api's outbox writer inserts, timed on the test clock. */
  private UUID publish(String type, String aggregateId, String payloadJson) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO outbox_message (id, event_type, aggregate_id, payload, occurred_at, status, next_attempt_at)
            VALUES (:id, :type, :aggregateId, CAST(:payload AS jsonb), :now, 'PENDING', :now)
            """)
        .param("id", id)
        .param("type", type)
        .param("aggregateId", aggregateId)
        .param("payload", payloadJson)
        .param("now", Timestamp.from(clock.instant()))
        .update();
    return id;
  }

  private UUID userRegistered(String email) {
    String userId = UUID.randomUUID().toString();
    return publish(
        "identity.user-registered.v1",
        userId,
        """
        {"userId":"%s","email":"%s","displayName":"Asha","verificationToken":"tok-123"}
        """
            .formatted(userId, email));
  }

  private JsonNode inbox() {
    return mailpitApi.get().uri("/messages").retrieve().body(JsonNode.class);
  }

  private String outboxStatus(UUID id) {
    return jdbc.sql("SELECT status FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private String delivery(UUID eventId) {
    return jdbc.sql("SELECT status || '/' || attempts FROM email_delivery WHERE event_id = :id")
        .param("id", eventId)
        .query(String.class)
        .single();
  }

  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO app_user (id, email, display_name, password_hash, email_verified_at, created_at, version)
            VALUES (:id, :email, 'Asha', '{noop}x', now(), now(), 0)
            """)
        .param("id", id)
        .param("email", email)
        .update();
    return id;
  }

  // ------------------------------------------------------------------------------------ tests

  @Test
  void aRedeliveredEventSendsNothingTheSecondTime() {
    UUID event = userRegistered("asha@example.com");

    assertThat(relay.relayOnce().delivered()).isEqualTo(1);
    assertThat(inbox().get("total").asInt()).isEqualTo(1);
    JsonNode mail = inbox().get("messages").get(0);
    assertThat(mail.get("Subject").asString()).isEqualTo("Confirm your email for Masternova");
    assertThat(mail.get("To").get(0).get("Address").asString()).isEqualTo("asha@example.com");

    // ⭐ the worker sent the email, then died before marking the outbox row DONE: the row comes
    //    back. At-least-once delivery means the HANDLER runs twice …
    jdbc.sql("UPDATE outbox_message SET status = 'PENDING', next_attempt_at = :now WHERE id = :id")
        .param("now", Timestamp.from(clock.instant()))
        .param("id", event)
        .update();
    assertThat(relay.relayOnce().delivered()).isEqualTo(1);

    // … and the delivery claim makes the EFFECT happen once
    assertThat(inbox().get("total").asInt()).isEqualTo(1);
    assertThat(delivery(event)).isEqualTo("SENT/1");
    assertThat(outboxStatus(event)).isEqualTo("DONE");
  }

  @Test
  void aWorkerThatDiedMidSendIsTakenOverOnceItsLeaseExpires() {
    UUID event = userRegistered("asha@example.com");
    // a previous worker claimed the outbox row AND the email, then crashed before sending
    jdbc.sql(
            """
            UPDATE outbox_message SET status = 'PROCESSING', attempts = 1, next_attempt_at = :leaseUntil
             WHERE id = :id
            """)
        .param("leaseUntil", Timestamp.from(clock.instant().plus(Duration.ofSeconds(60))))
        .param("id", event)
        .update();
    deliveries.claim(new DeliveryKey(event, "verify-email", "asha@example.com"));

    clock.advance(Duration.ofSeconds(30)); // within the lease: the dead worker might still be alive
    assertThat(relay.relayOnce().total()).isZero();
    assertThat(inbox().get("total").asInt()).isZero();

    clock.advance(Duration.ofSeconds(31)); // lease over: take it over
    assertThat(relay.relayOnce().delivered()).isEqualTo(1);

    assertThat(inbox().get("total").asInt()).isEqualTo(1);
    assertThat(delivery(event)).isEqualTo("SENT/2"); // the second claim of the same email
    assertThat(outboxStatus(event)).isEqualTo("DONE");
  }

  @Test
  void theWelcomeEmailCarriesUnsubscribeHeadersAndAnOptOutStopsTheNextOne() {
    UUID userId = insertUser("asha@example.com");
    String verified =
        """
        {"userId":"%s","email":"asha@example.com","displayName":"Asha"}
        """
            .formatted(userId);
    publish("identity.email-verified.v1", userId.toString(), verified);
    relay.relayOnce();

    String id = inbox().get("messages").get(0).get("ID").asString();
    JsonNode headers =
        mailpitApi.get().uri("/message/{id}/headers", id).retrieve().body(JsonNode.class);
    assertThat(headers.get("List-Unsubscribe").get(0).asString())
        .startsWith("<http://localhost:8081/api/v1/notifications/unsubscribe/one-click?token=");
    assertThat(headers.get("List-Unsubscribe-Post").get(0).asString())
        .isEqualTo("List-Unsubscribe=One-Click");
    JsonNode message = mailpitApi.get().uri("/message/{id}", id).retrieve().body(JsonNode.class);
    assertThat(message.get("Text").asString()).contains("http://localhost:8081/unsubscribe?token=");

    // the user opts out of PRODUCT_NEWS (what the api's unsubscribe endpoint writes)
    jdbc.sql(
            """
            INSERT INTO notification_preference (user_id, category, enabled, updated_at)
            VALUES (:user, 'PRODUCT_NEWS', false, now())
            """)
        .param("user", userId)
        .update();
    UUID second = publish("identity.email-verified.v1", userId.toString(), verified);
    relay.relayOnce();

    assertThat(inbox().get("total").asInt()).isEqualTo(1); // nothing new arrived
    assertThat(delivery(second)).isEqualTo("SUPPRESSED/0");
    assertThat(outboxStatus(second)).isEqualTo("DONE");
  }
}
