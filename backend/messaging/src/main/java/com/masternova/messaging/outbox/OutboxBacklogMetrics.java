package com.masternova.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;

/**
 * Gauges over what the relay still owes, read from the table at scrape time. ⭐ They catch what the
 * relay's own counters can't: a relay that stopped (crashed thread, stuck handler) records nothing
 * at all, but its backlog keeps growing and the oldest message keeps ageing.
 *
 * <p>A {@link MeterBinder}: Boot binds every such bean to the app's registry when it starts.
 */
class OutboxBacklogMetrics implements MeterBinder {

  private final OutboxRepository repository;
  private final Clock clock;

  OutboxBacklogMetrics(OutboxRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("outbox.backlog", repository, r -> r.backlog().size())
        .description("Outbox messages not yet delivered (pending or being delivered)")
        .register(registry);
    Gauge.builder("outbox.oldest.age", this, OutboxBacklogMetrics::oldestAgeSeconds)
        .description("Age of the oldest undelivered outbox message; 0 when nothing is owed")
        .baseUnit("seconds")
        .register(registry);
  }

  double oldestAgeSeconds() {
    var oldest = repository.backlog().oldest();
    return oldest == null ? 0 : Duration.between(oldest, clock.instant()).toMillis() / 1000.0;
  }
}
