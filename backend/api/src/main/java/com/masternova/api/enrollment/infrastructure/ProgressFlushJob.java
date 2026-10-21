package com.masternova.api.enrollment.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flushes the progress buffer every 30 s (ADR-0022) — on ONE api replica: ShedLock holds the lock
 * for at least 25 s, so N replicas' schedules still make one flush per interval, not N.
 *
 * <p>A failed flush (Postgres or Redis down) is logged and timed as {@code error}; the batch waits
 * in Redis for the next tick. Disabled with {@code masternova.enrollment.flush-enabled=false}
 * (tests that flush by hand).
 */
@Component
@ConditionalOnProperty(
    name = "masternova.enrollment.flush-enabled",
    havingValue = "true",
    matchIfMissing = true)
class ProgressFlushJob {

  private static final Logger log = LoggerFactory.getLogger(ProgressFlushJob.class);

  private final WriteBackProgressStore store;
  private final MeterRegistry meters;

  ProgressFlushJob(WriteBackProgressStore store, MeterRegistry meters) {
    this.store = store;
    this.meters = meters;
  }

  @Scheduled(
      fixedDelayString = "${masternova.enrollment.flush-every:30s}",
      initialDelayString = "${masternova.enrollment.flush-every:30s}")
  @SchedulerLock(name = "progress-flush", lockAtMostFor = "5m", lockAtLeastFor = "25s")
  void flush() {
    Timer.Sample sample = Timer.start(meters);
    String outcome = "ok";
    try {
      store.flush();
    } catch (RuntimeException e) { // the batch stays in Redis; never kill the scheduler thread
      outcome = "error";
      log.warn("progress flush failed, retrying next tick", e);
    } finally {
      sample.stop(meters.timer("enrollment.progress.flush", "outcome", outcome));
    }
  }
}
