package com.masternova.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Gauges over what the relay still owes, read from the table at scrape time. ⭐ They catch what the
 * relay's own counters can't: a relay that stopped (crashed thread, stuck handler, switched off)
 * records nothing at all, but its overdue messages keep waiting.
 *
 * <ul>
 *   <li>{@code outbox.backlog}: rows not delivered yet;
 *   <li>{@code outbox.overdue}: how long the most overdue DUE row has waited. A working relay
 *       claims due rows within a poll (~1 s), and rows waiting out a retry backoff are not due — so
 *       this only grows when nobody is relaying (the {@code OutboxStalled} alert).
 * </ul>
 *
 * <p>Registered in every app that writes the outbox, relay or not: a worker whose relay is off must
 * still show the debt. Both gauges read ONE query per scrape (a 1 s snapshot).
 */
class OutboxBacklogMetrics implements MeterBinder {

  private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(1);

  private final OutboxRepository repository;
  private final Clock clock;
  private volatile Snapshot snapshot;

  private record Snapshot(Instant takenAt, OutboxRepository.Backlog backlog) {}

  OutboxBacklogMetrics(OutboxRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("outbox.backlog", this, m -> m.backlog().size())
        .description("Outbox messages not yet delivered (pending, backing off, or being delivered)")
        .register(registry);
    Gauge.builder("outbox.overdue", this, OutboxBacklogMetrics::overdueSeconds)
        .description("How long the most overdue due outbox message has waited; ~0 while relaying")
        .baseUnit("seconds")
        .register(registry);
  }

  double overdueSeconds() {
    Instant oldestDue = backlog().oldestDue();
    return oldestDue == null
        ? 0
        : Math.max(0, Duration.between(oldestDue, clock.instant()).toMillis() / 1000.0);
  }

  private OutboxRepository.Backlog backlog() {
    Instant now = clock.instant();
    Snapshot current = snapshot;
    if (current == null || !now.isBefore(current.takenAt().plus(SNAPSHOT_TTL))) {
      current = new Snapshot(now, repository.backlog());
      snapshot = current;
    }
    return current.backlog();
  }
}
