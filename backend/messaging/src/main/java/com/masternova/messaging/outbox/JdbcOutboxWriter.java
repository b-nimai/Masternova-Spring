package com.masternova.messaging.outbox;

import com.masternova.kernel.event.DomainEvent;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.SenderContext;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises the event to JSON and appends it as a PENDING outbox row — through the caller's
 * transaction ({@code JdbcClient} joins whatever transaction is active on this thread).
 *
 * <p>⭐ The append is a PRODUCER observation: with tracing on, Boot's propagating handler writes the
 * new span's {@code traceparent} into the row's headers, and the relay continues the trace from
 * there (ADR-0015). With tracing off the headers stay empty — nothing else changes.
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
  private final ObservationRegistry observations;

  JdbcOutboxWriter(
      OutboxRepository repository,
      JsonMapper json,
      Clock clock,
      MeterRegistry meters,
      ObservationRegistry observations) {
    this.repository = repository;
    this.json = json;
    this.clock = clock;
    this.meters = meters;
    this.observations = observations;
  }

  @Override
  public void append(DomainEvent event) {
    SenderContext<Map<String, String>> context = new SenderContext<>(Map::put, Kind.PRODUCER);
    context.setCarrier(new HashMap<>());
    context.setRemoteServiceName("outbox");
    Observation.createNotStarted("outbox.publish", () -> context, observations)
        .contextualName("outbox publish " + event.type())
        .lowCardinalityKeyValue("event.type", event.type())
        .observe(
            () ->
                repository.append(
                    new OutboxRepository.NewMessage(
                        UUID.randomUUID(),
                        event.type(),
                        event.aggregateId(),
                        json.writeValueAsString(event), // a record → its components as JSON
                        clock.instant(),
                        context.getCarrier()))); // the tracing handler filled it on start
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
