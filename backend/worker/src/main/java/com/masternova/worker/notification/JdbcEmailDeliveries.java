package com.masternova.worker.notification;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The claim in ONE statement — the same "unique constraint as a lock" idea as the idempotency keys
 * (Phase 2.6), with a state machine on top.
 */
@Repository
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "ConcreteRepository",
    note = "patterns/docs/16-repository-unit-of-work.md")
class JdbcEmailDeliveries implements EmailDeliveries {

  // ⭐ INSERT a new SENDING row — or, if the email already has a row, take it over ONLY when that
  //    is legal: the last attempt FAILED, or a SENDING claim is older than the lease (its worker
  //    died mid-send). In every other case (SENT, BOUNCED, SUPPRESSED, a fresh SENDING) the WHERE
  //    is false, nothing is updated, and RETURNING yields no row. Concurrent claimers serialise on
  //    the unique index: exactly one of them gets a row back.
  private static final String CLAIM =
      """
      INSERT INTO email_delivery (id, event_id, template, recipient, status, attempts, created_at, updated_at)
      VALUES (:id, :eventId, :template, :recipient, 'SENDING', 1, :now, :now)
      ON CONFLICT (event_id, template, recipient) DO UPDATE
         SET status = 'SENDING', attempts = email_delivery.attempts + 1, last_error = NULL, updated_at = :now
       WHERE email_delivery.status = 'FAILED'
          OR (email_delivery.status = 'SENDING' AND email_delivery.updated_at < :staleBefore)
      RETURNING id, attempts
      """;

  private final JdbcClient jdbc;
  private final Clock clock;
  private final Duration lease;

  JdbcEmailDeliveries(JdbcClient jdbc, Clock clock, OutboxProperties outbox) {
    this.jdbc = jdbc;
    this.clock = clock;
    // a send can legitimately take as long as the outbox lease that covers its message
    this.lease = outbox.lease();
  }

  @Override
  public Claim claim(DeliveryKey key) {
    Instant now = clock.instant();
    Optional<Claim> claimed =
        jdbc.sql(CLAIM)
            .param("id", UUID.randomUUID())
            .param("eventId", key.eventId())
            .param("template", key.template())
            .param("recipient", key.recipient())
            .param("now", Timestamp.from(now))
            .param("staleBefore", Timestamp.from(now.minus(lease)))
            .query(
                (rs, row) ->
                    (Claim)
                        new Claim.Claimed(rs.getObject("id", UUID.class), rs.getInt("attempts")))
            .optional();
    return claimed.orElseGet(() -> existing(key));
  }

  /** No row came back: read why. */
  private Claim existing(DeliveryKey key) {
    String status =
        jdbc.sql(
                // ⭐ CAST: a JDBC string parameter is `text`, and citext = text compares as TEXT —
                //    case-SENSITIVE. The unique index (citext) and this lookup must agree.
                """
                SELECT status FROM email_delivery
                 WHERE event_id = :eventId AND template = :template AND recipient = CAST(:recipient AS citext)
                """)
            .param("eventId", key.eventId())
            .param("template", key.template())
            .param("recipient", key.recipient())
            .query(String.class)
            .single();
    return switch (status) {
      case "SENT" -> new Claim.AlreadySent();
      case "SENDING" -> new Claim.InFlight();
      default -> new Claim.Final(status); // BOUNCED, SUPPRESSED
    };
  }

  @Override
  public void markSent(UUID deliveryId, String providerMessageId) {
    update(deliveryId, "SENT", null, providerMessageId);
  }

  @Override
  public void markFailed(UUID deliveryId, String error) {
    update(deliveryId, "FAILED", error, null);
  }

  @Override
  public void markBounced(UUID deliveryId, String error) {
    update(deliveryId, "BOUNCED", error, null);
  }

  private void update(UUID id, String status, String error, String providerMessageId) {
    jdbc.sql(
            """
            UPDATE email_delivery
               SET status = :status, last_error = :error,
                   provider_message_id = COALESCE(:providerMessageId, provider_message_id), updated_at = :now
             WHERE id = :id
            """)
        .param("status", status)
        .param("error", error)
        .param("providerMessageId", providerMessageId)
        .param("now", Timestamp.from(clock.instant()))
        .param("id", id)
        .update();
  }

  @Override
  public void recordSuppressed(DeliveryKey key, String reason) {
    Timestamp now = Timestamp.from(clock.instant());
    jdbc.sql(
            """
            INSERT INTO email_delivery (id, event_id, template, recipient, status, attempts, last_error, created_at, updated_at)
            VALUES (:id, :eventId, :template, :recipient, 'SUPPRESSED', 0, :reason, :now, :now)
            ON CONFLICT (event_id, template, recipient) DO NOTHING
            """)
        .param("id", UUID.randomUUID())
        .param("eventId", key.eventId())
        .param("template", key.template())
        .param("recipient", key.recipient())
        .param("reason", reason)
        .param("now", now)
        .update();
  }
}
