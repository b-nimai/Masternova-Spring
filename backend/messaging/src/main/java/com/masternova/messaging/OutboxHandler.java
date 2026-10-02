package com.masternova.messaging;

/**
 * Reacts to one event type delivered from the outbox — a durable OBSERVER (it survives crashes and
 * is retried with backoff). Register one as a bean in the process that runs the relay (the worker,
 * ADR-0008); the relay finds it by {@link #eventType()}.
 *
 * <p>⭐ Delivery is AT-LEAST-ONCE: after a crash or a timeout the same message can arrive again.
 * Every handler must be idempotent — for an external effect like an email that means a claim with
 * its own state, not just "remember it was processed" (docs/lld/notification.md §6).
 */
public interface OutboxHandler {

  /**
   * The {@code DomainEvent.type()} this handler consumes, e.g. {@code identity.user-registered.v1}.
   */
  String eventType();

  /** Throw to signal failure: the message is retried with backoff, then parked as DEAD. */
  void handle(OutboxMessage message);
}
