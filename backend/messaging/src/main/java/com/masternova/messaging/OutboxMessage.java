package com.masternova.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One event as the relay delivers it to an {@link OutboxHandler}. The payload is the event's JSON
 * (handlers deserialize it into their own view of the event).
 *
 * @param attempts how many times delivery has been tried, INCLUDING this one
 */
public record OutboxMessage(
    UUID id, String type, String aggregateId, String payload, Instant occurredAt, int attempts) {

  public OutboxMessage {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(aggregateId, "aggregateId");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
