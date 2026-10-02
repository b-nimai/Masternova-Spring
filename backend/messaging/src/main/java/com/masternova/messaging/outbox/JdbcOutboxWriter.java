package com.masternova.messaging.outbox;

import com.masternova.kernel.event.DomainEvent;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxWriter;
import java.time.Clock;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises the event to JSON and appends it as a PENDING outbox row — through the caller's
 * transaction ({@code JdbcClient} joins whatever transaction is active on this thread).
 */
@DesignPattern(
    value = Pattern.TRANSACTIONAL_OUTBOX,
    role = "Writer",
    note = "patterns/docs/17-transactional-outbox.md")
class JdbcOutboxWriter implements OutboxWriter {

  private final OutboxRepository repository;
  private final JsonMapper json;
  private final Clock clock;

  JdbcOutboxWriter(OutboxRepository repository, JsonMapper json, Clock clock) {
    this.repository = repository;
    this.json = json;
    this.clock = clock;
  }

  @Override
  public void append(DomainEvent event) {
    repository.append(
        new OutboxRepository.NewMessage(
            UUID.randomUUID(),
            event.type(),
            event.aggregateId(),
            json.writeValueAsString(event), // a record → its components as JSON
            clock.instant()));
  }
}
