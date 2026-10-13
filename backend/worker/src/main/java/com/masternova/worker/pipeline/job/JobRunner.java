package com.masternova.worker.pipeline.job;

import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.ffmpeg.MediaToolException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs ONE claimed job to its outcome (video-pipeline LLD §5):
 *
 * <ul>
 *   <li>while the processor works, a heartbeat extends the lease and records progress (live ticks
 *       go to Redis through {@link ProgressPublisher}) — no transaction is open while ffmpeg runs;
 *   <li>success: ONE transaction = lock the asset + mark DONE (only if the lease is still ours) +
 *       the processor's effects + enqueue what it unlocked. A worker that lost its lease changes
 *       nothing — its outputs were the same keys anyway;
 *   <li>failure: permanent (the input) → DEAD at once, asset FAILED; temporary → back to PENDING
 *       with backoff (30 s × 4ⁿ ± 20 %) until {@code max-attempts}, then DEAD.
 * </ul>
 */
@Component
public class JobRunner {

  private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

  private final JobProcessorRegistry registry;
  private final JobRepository jobs;
  private final PipelineStore store;
  private final TransactionTemplate tx;
  private final PipelineProperties props;
  private final Clock clock;
  private final ProgressPublisher publisher;

  JobRunner(
      JobProcessorRegistry registry,
      JobRepository jobs,
      PipelineStore store,
      TransactionTemplate tx,
      PipelineProperties props,
      Clock clock,
      ProgressPublisher publisher) {
    this.registry = registry;
    this.jobs = jobs;
    this.store = store;
    this.tx = tx;
    this.props = props;
    this.clock = clock;
    this.publisher = publisher;
  }

  public void execute(Job job, String workerId) {
    AtomicInteger progress = new AtomicInteger(-1);
    Thread heartbeat = Thread.ofVirtual().start(() -> heartbeat(job, workerId, progress));
    try {
      JobProcessor.Completion completion =
          registry
              .of(job.type())
              .run(
                  job,
                  props.workDir(),
                  percent -> {
                    progress.set(percent);
                    publisher.progress(job, percent);
                  });
      heartbeat.interrupt();
      tx.executeWithoutResult(
          status -> {
            store.lockAsset(job.assetId()); // ⭐ siblings finishing at once serialise here
            if (!jobs.complete(job.id(), workerId)) {
              log.warn("job {} finished after losing its lease; its rerun owns it now", job.id());
              return;
            }
            completion.apply(store).forEach(jobs::enqueue);
          });
    } catch (RuntimeException e) {
      heartbeat.interrupt();
      fail(job, workerId, e);
    } finally {
      publisher.changed(job); // after the commit: subscribers re-read the asset
    }
  }

  private void heartbeat(Job job, String workerId, AtomicInteger progress) {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(props.heartbeat());
        int p = progress.get();
        if (!jobs.heartbeat(job.id(), workerId, props.lease(), p < 0 ? null : p)) {
          log.warn("job {} lost its lease", job.id());
          return;
        }
      }
    } catch (InterruptedException done) {
      // the job finished
    }
  }

  private void fail(Job job, String workerId, RuntimeException e) {
    boolean permanent = e instanceof MediaToolException m && m.permanent();
    String error = e.getClass().getSimpleName() + ": " + e.getMessage();
    if (permanent || job.attempts() >= props.maxAttempts()) {
      log.error("job {} ({} {}) is DEAD: {}", job.id(), job.type(), job.rung(), error, e);
      tx.executeWithoutResult(
          status -> {
            if (jobs.bury(job.id(), workerId, error)) {
              store.markFailed(job.assetId(), permanent ? "MEDIA_UNREADABLE" : "TRANSCODE_FAILED");
            }
          });
      return;
    }
    Duration delay = backoff(job.attempts());
    log.warn(
        "job {} failed (attempt {}), retrying in {}: {}", job.id(), job.attempts(), delay, error);
    jobs.reschedule(job.id(), workerId, clock.instant().plus(delay), error);
  }

  /** 30 s, 2 m, 8 m, 32 m … ± 20 % jitter, so a storage outage doesn't retry every job at once. */
  Duration backoff(int attempt) {
    long base = props.baseBackoff().toMillis() * (1L << (2 * (attempt - 1)));
    double jitter = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
    return Duration.ofMillis((long) (base * jitter));
  }
}
