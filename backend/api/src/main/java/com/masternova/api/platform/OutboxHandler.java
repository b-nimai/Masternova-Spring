package com.masternova.api.platform;

/**
 * Reacts to one event type delivered from the outbox — a durable OBSERVER (it survives crashes and
 * is retried with backoff). Part of the platform module API: any module registers one as a bean.
 *
 * <p>⭐ Delivery is AT-LEAST-ONCE: after a crash or a timeout the same message can arrive again.
 * Every handler must be idempotent (Phase 4 adds the processed-event table for that).
 */
public interface OutboxHandler {

  /**
   * The {@code DomainEvent.type()} this handler consumes, e.g. {@code identity.user-registered.v1}.
   */
  String eventType();

  /** Throw to signal failure: the message is retried with backoff, then parked as DEAD. */
  void handle(OutboxMessage message);
}
