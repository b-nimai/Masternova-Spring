package com.masternova.api.platform;

import com.masternova.kernel.event.DomainEvent;

/**
 * How a module announces that something happened. Part of the platform module's public API.
 *
 * <p>⭐ Must be called INSIDE the caller's transaction (it throws otherwise): the event is then
 * committed or rolled back together with the state change that caused it — never one without the
 * other. From Phase 2.4 the event is also written to the transactional outbox for reliable,
 * cross-process delivery.
 */
public interface EventPublisher {

  void publish(DomainEvent event);
}
