package com.masternova.messaging.outbox;

import com.masternova.kernel.event.DomainEvent;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
  private final MeterRegistry meters;

  JdbcOutboxWriter(
      OutboxRepository repository, JsonMapper json, Clock clock, MeterRegistry meters) {
    this.repository = repository;
    this.json = json;
    this.clock = clock;
    this.meters = meters;
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
    countOnCommit(event.type());
  }

  /**
   * {@code outbox.events.written} counts business facts (registrations, uploads, later orders) by
   * type. ⭐ Only once the transaction COMMITS: a rolled-back change wrote no row, so it must not
   * show up in the metric either.
   */
  private void countOnCommit(String type) {
    Counter written =
        Counter.builder("outbox.events.written")
            .description("Domain events committed to the outbox, by type")
            .tag("event.type", type)
            .register(meters);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      written.increment(); // no transaction: the row is already committed (auto-commit)
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            written.increment();
          }
        });
  }
}
