package com.masternova.api.commerce.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * The provider events we have processed (ADR-0018). An interface in {@code domain/} with its JDBC
 * implementation in {@code infrastructure/}, like entitlement's repository: the claim is one {@code
 * INSERT … ON CONFLICT DO NOTHING}, which JPA can't say.
 */
public interface WebhookInbox {

  /** How a claimed event ended, stored for support. */
  enum Outcome {
    /** It moved an order. */
    PROCESSED,
    /** Nothing to do: a type we don't act on, or the state machine ignored it. */
    IGNORED,
    /** About an order that isn't ours (another integration on the same account, a test event). */
    UNKNOWN_ORDER
  }

  /**
   * ⭐ Claims the event for THIS transaction. A concurrent claim of the same id waits for this
   * transaction: if it commits, the other gets {@code false}; if it rolls back, the other claims.
   *
   * @return {@code false} if the event was already processed — do nothing
   */
  boolean claim(
      String provider,
      String eventId,
      String eventType,
      Optional<String> providerOrderId,
      Instant now);

  void record(String provider, String eventId, Outcome outcome);
}
