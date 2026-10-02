package com.masternova.messaging;

import com.masternova.kernel.event.DomainEvent;

/**
 * Appends an event to the outbox in the CURRENT transaction, so it commits — or rolls back —
 * together with the business change that caused it. The api's {@code EventPublisher} calls it; the
 * worker's relay later delivers the row. An interface so callers can be unit-tested with a lambda.
 */
@FunctionalInterface
public interface OutboxWriter {

  void append(DomainEvent event);
}
