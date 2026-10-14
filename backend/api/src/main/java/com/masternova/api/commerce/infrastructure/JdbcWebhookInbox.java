package com.masternova.api.commerce.infrastructure;

import com.masternova.api.commerce.domain.WebhookInbox;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link WebhookInbox} over {@code payment_webhook_event} (V20). */
@Repository
class JdbcWebhookInbox implements WebhookInbox {

  private final JdbcClient jdbc;

  JdbcWebhookInbox(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean claim(
      String provider,
      String eventId,
      String eventType,
      Optional<String> providerOrderId,
      Instant now) {
    return jdbc.sql(
                """
                INSERT INTO payment_webhook_event
                       (provider, event_id, event_type, provider_order_id, received_at)
                VALUES (:provider, :eventId, :type, :orderId, :now)
                ON CONFLICT (provider, event_id) DO NOTHING
                """)
            .param("provider", provider)
            .param("eventId", eventId)
            .param("type", eventType)
            .param("orderId", providerOrderId.orElse(null))
            .param("now", Timestamp.from(now))
            .update()
        == 1;
  }

  @Override
  public void record(String provider, String eventId, Outcome outcome) {
    jdbc.sql(
            "UPDATE payment_webhook_event SET outcome = :outcome"
                + " WHERE provider = :provider AND event_id = :eventId")
        .param("outcome", outcome.name())
        .param("provider", provider)
        .param("eventId", eventId)
        .update();
  }
}
