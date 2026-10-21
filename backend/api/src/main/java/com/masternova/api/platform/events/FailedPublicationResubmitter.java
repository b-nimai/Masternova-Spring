package com.masternova.api.platform.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries {@code @ApplicationModuleListener}s that failed (ADR-0021). Modulith marks a publication
 * {@code FAILED} when its listener throws, and — through the staleness settings — when its process
 * died before or during the listener. This job hands those back to their listeners.
 *
 * <p>⭐ Scheduled on ONE replica (ShedLock), never "republish on restart": that would replay
 * everything on every replica at every rollout, while the old pods may still be running it.
 * Listeners are idempotent anyway; this only avoids the wasted work.
 *
 * <p>A publication that failed {@value #MAX_ATTEMPTS} times is left alone (a poison event, or a bug
 * that needs a deploy) and stays visible in {@code events.publications.failed}. Modulith reads the
 * oldest 100 FAILED rows before filtering, so more than 100 given-up rows would starve the rest:
 * the gauge alerts long before that.
 */
@Component
class FailedPublicationResubmitter implements MeterBinder {

  static final int MAX_ATTEMPTS = 10;
  static final Duration MIN_AGE = Duration.ofMinutes(1);
  private static final Logger log = LoggerFactory.getLogger(FailedPublicationResubmitter.class);

  private final FailedEventPublications failed;
  private final JdbcClient jdbc;
  private final Clock clock;

  FailedPublicationResubmitter(FailedEventPublications failed, JdbcClient jdbc, Clock clock) {
    this.failed = failed;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${masternova.events.resubmit-every:60s}", initialDelay = 30_000)
  @SchedulerLock(name = "event-publication-resubmit", lockAtMostFor = "5m")
  void resubmit() {
    try {
      Instant publishedBefore = clock.instant().minus(MIN_AGE);
      failed.resubmit(
          ResubmissionOptions.defaults()
              .withBatchSize(100)
              // ⭐ the age check is OURS: Modulith 2.1.1's query applies withMinAge() only to rows
              //    with no status — its SQL reads `STATUS = 'FAILED' OR (…) AND PUBLICATION_DATE <
              // ?`
              //    and AND binds tighter — so every FAILED row came back however young
              //    (FailedPublicationResubmitterIT). The filter runs before a row is claimed.
              .withFilter(
                  p ->
                      p.getPublicationDate().isBefore(publishedBefore)
                          && p.getCompletionAttempts() < MAX_ATTEMPTS));
    } catch (RuntimeException e) { // the next tick tries again; never kill the scheduler thread
      log.warn("resubmitting failed event publications failed", e);
    }
  }

  /** How many module-listener deliveries are stuck in FAILED — alert on > 0 for long. */
  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("events.publications.failed", this, FailedPublicationResubmitter::countFailed)
        .description("Module-listener publications in FAILED (ADR-0021)")
        .register(registry);
  }

  private double countFailed() {
    return jdbc.sql("SELECT count(*) FROM event_publication WHERE status = 'FAILED'")
        .query(Long.class)
        .single();
  }
}
