package com.masternova.kernel.event;

/**
 * Something that HAPPENED in the domain — named in the past tense ({@code UserRegistered}, {@code
 * OrderPaid}), immutable (a record), and published by the module that owns the change.
 *
 * <p>Lives in the kernel because both deployables need it: the api publishes events, the worker
 * consumes them from the outbox.
 */
public interface DomainEvent {

  /**
   * Stable, versioned name used in the outbox and on the wire, e.g. {@code
   * "identity.user-registered.v1"}. ⭐ Never change it once published — publish {@code .v2}
   * alongside instead, so old rows still deserialize.
   */
  String type();

  /** The aggregate the event is about (a course id, an order id) — for ordering and tracing. */
  String aggregateId();
}
