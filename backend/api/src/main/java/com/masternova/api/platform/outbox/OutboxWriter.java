package com.masternova.api.platform.outbox;

import com.masternova.kernel.event.DomainEvent;

/**
 * Appends an event to the outbox in the CURRENT transaction. Public only so the platform's events
 * sub-package can use it — still INTERNAL to the platform module (Modulith verifies that). An
 * interface so the publisher can be unit-tested with a lambda.
 */
@FunctionalInterface
public interface OutboxWriter {

  void append(DomainEvent event);
}
