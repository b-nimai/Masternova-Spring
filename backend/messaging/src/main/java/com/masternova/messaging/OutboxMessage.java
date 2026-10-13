package com.masternova.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One event as the relay delivers it to an {@link OutboxHandler}. The payload is the event's JSON
 * (handlers deserialize it into their own view of the event).
 *
 * @param attempts how many times delivery has been tried, INCLUDING this one
 * @param headers propagation fields written with the event ({@code traceparent} …); empty when the
 *     writer had no trace. The relay reads them, handlers rarely need to.
 */
public record OutboxMessage(
    UUID id,
    String type,
    String aggregateId,
    String payload,
    Instant occurredAt,
    int attempts,
    Map<String, String> headers) {

  public OutboxMessage {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(aggregateId, "aggregateId");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(occurredAt, "occurredAt");
    headers = headers == null ? Map.of() : Map.copyOf(headers);
  }

  /** A message without headers (tests, events written before D3.3). */
  public OutboxMessage(
      UUID id, String type, String aggregateId, String payload, Instant occurredAt, int attempts) {
    this(id, type, aggregateId, payload, occurredAt, attempts, Map.of());
  }
}
