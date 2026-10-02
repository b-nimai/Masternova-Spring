package com.masternova.worker.notification;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.UUID;

/**
 * The per-email state machine (docs/lld/notification.md §3), behind an interface so the sending
 * rules are unit-tested with an in-memory fake and the SQL is proven once against Postgres.
 *
 * <pre>
 *   claim ──► SENDING ──► SENT
 *                │
 *                ├──► FAILED ──(redelivery)──► SENDING again
 *                └──► BOUNCED          refused ──► SUPPRESSED
 * </pre>
 */
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "Repository",
    note = "patterns/docs/16-repository-unit-of-work.md")
public interface EmailDeliveries {

  /** The outcome of trying to take the right to send — sealed: callers handle every case. */
  sealed interface Claim {
    /** This process now owns the send. {@code attempt} counts deliveries of THIS email. */
    record Claimed(UUID deliveryId, int attempt) implements Claim {}

    /** Already delivered: a redelivered event must send nothing. */
    record AlreadySent() implements Claim {}

    /** Deliberately not sent (SUPPRESSED) or permanently undeliverable (BOUNCED). */
    record Final(String status) implements Claim {}

    /** Another process claimed it moments ago and is still within its lease. */
    record InFlight() implements Claim {}
  }

  Claim claim(DeliveryKey key);

  void markSent(UUID deliveryId, String providerMessageId);

  void markFailed(UUID deliveryId, String error);

  void markBounced(UUID deliveryId, String error);

  /** Records a deliberate non-send. A no-op if the email already has a row. */
  void recordSuppressed(DeliveryKey key, String reason);
}
