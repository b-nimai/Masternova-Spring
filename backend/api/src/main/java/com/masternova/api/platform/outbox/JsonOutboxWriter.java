package com.masternova.api.platform.outbox;

import com.masternova.kernel.event.DomainEvent;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Serialises the event to JSON and appends it as a PENDING outbox row. */
@Component
@DesignPattern(
    value = Pattern.TRANSACTIONAL_OUTBOX,
    role = "Writer",
    note = "patterns/docs/17-transactional-outbox.md")
class JsonOutboxWriter implements OutboxWriter {

  private final OutboxRepository repository;
  private final JsonMapper json;
  private final Clock clock;

  JsonOutboxWriter(OutboxRepository repository, JsonMapper json, Clock clock) {
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
