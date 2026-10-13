package com.masternova.worker.pipeline.job;

import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.ffmpeg.MediaToolException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 *
 * <p>Every run is one {@code pipeline.job} observation: a timer by job type and outcome (the
 * transcode-duration metric) and, with tracing on, a span.
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
  private final ObservationRegistry observations;
  private final Map<DeadReason, Counter> dead = new EnumMap<>(DeadReason.class);
  private volatile boolean shuttingDown;

  JobRunner(
      JobProcessorRegistry registry,
      JobRepository jobs,
      PipelineStore store,
      TransactionTemplate tx,
      PipelineProperties props,
      Clock clock,
      ProgressPublisher publisher,
      ObservationRegistry observations,
      MeterRegistry meters) {
    this.registry = registry;
    this.jobs = jobs;
    this.store = store;
    this.tx = tx;
    this.props = props;
    this.clock = clock;
    this.publisher = publisher;
    this.observations = observations;
    // ⭐ created at 0 at startup: Prometheus' increase() can't see a series BORN at 1, so the
    //    first buried job would never reach the DeadLetters alert
    for (DeadReason reason : DeadReason.values()) {
      dead.put(
          reason,
          Counter.builder("pipeline.jobs.dead")
              .description("Pipeline jobs given up on for good (their asset is FAILED)")
              .tag("reason", tag(reason))
              .register(meters));
    }
  }

  /** Why a job was buried — the {@code reason} tag of {@code pipeline.jobs.dead}. */
  enum DeadReason {
    PERMANENT, // the input itself (ffprobe can't read it)
    ATTEMPTS, // temporary failures until max-attempts
    ABANDONED // its worker died on the last attempt (OOM-kill, SIGKILL)
  }

  /** How a run ended — the {@code outcome} tag of {@code pipeline.job}. */
  enum Outcome {
    DONE,
    LOST_LEASE,
    RETRY,
    DEAD,
    RELEASED,
    ABORTED // an Error, or recording the failure failed too (the database is gone)
  }

  public void execute(Job job, String workerId) {
    // ⭐ low cardinality (type, outcome) → metric tags; high cardinality (ids) → span only
    Observation observation =
        Observation.createNotStarted("pipeline.job", observations)
            .lowCardinalityKeyValue("job.type", job.type().name())
            .highCardinalityKeyValue("asset.id", job.assetId().toString())
            .highCardinalityKeyValue("job.attempt", Integer.toString(job.attempts()))
            // ⭐ replaced only when a path COMPLETES: a run that ends in an Error, or whose
            //    fail()/release() throws, is never timed as "done"
            .lowCardinalityKeyValue("outcome", tag(Outcome.ABORTED));
    try {
      // observe() records any Throwable (Error included) and stops the observation; its scope
      // covers the failure handling too, so those log lines carry this run's trace id
      observation.observe(
          () ->
              observation.lowCardinalityKeyValue(
                  "outcome", tag(attempt(job, workerId, observation))));
    } finally {
      publisher.changed(job); // after the commit: subscribers re-read the asset
    }
  }

  private Outcome attempt(Job job, String workerId, Observation observation) {
    try {
      return run(job, workerId);
    } catch (RuntimeException e) {
      observation.error(e);
      if (shuttingDown) {
        // ⭐ our deploy / scale-in interrupted it, whatever step it was in (ffmpeg, an upload…):
        //    not the media's fault — give it back uncounted, due now
        jobs.release(job.id(), workerId);
        return Outcome.RELEASED;
      }
      return fail(job, workerId, e);
    }
  }

  /** {@code DONE} → {@code "done"}: the tag values of both enums below. */
  private static String tag(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  private Outcome run(Job job, String workerId) {
    JobProcessor.Completion completion = runWithHeartbeat(job, workerId);
    Boolean completed =
        tx.execute(
            status -> {
              store.lockAsset(job.assetId()); // ⭐ siblings finishing at once serialise here
              if (!jobs.complete(job.id(), workerId)) {
                log.warn("job {} finished after losing its lease; its rerun owns it now", job.id());
                return false;
              }
              completion.apply(store).forEach(jobs::enqueue);
              return true;
            });
    return Boolean.TRUE.equals(completed) ? Outcome.DONE : Outcome.LOST_LEASE;
  }

  /** The processor's work, with the lease kept alive meanwhile — stopped on EVERY exit path. */
  private JobProcessor.Completion runWithHeartbeat(Job job, String workerId) {
    AtomicInteger progress = new AtomicInteger(-1);
    Thread heartbeat = Thread.ofVirtual().start(() -> heartbeat(job, workerId, progress));
    try {
      return registry
          .of(job.type())
          .run(
              job,
              props.workDir(),
              percent -> {
                progress.set(percent);
                publisher.progress(job, percent);
              });
    } finally {
      heartbeat
          .interrupt(); // ⭐ also on an Error (OOM…): a live heartbeat would hold the lease forever
    }
  }

  /** From now on, a failing job is this process going away, not a failure (JobPoller.close). */
  void shutDown() {
    shuttingDown = true;
  }

  /**
   * ⭐ Jobs whose worker DIED on their last allowed attempt: DEAD and their asset FAILED, in ONE
   * transaction — a source that OOM-kills every worker must stop being retried (the attempt check
   * in {@link #fail} never runs after a SIGKILL).
   */
  public void buryAbandoned() {
    List<UUID> buried =
        tx.execute(
            status -> {
              List<UUID> assets = jobs.buryAbandoned(props.maxAttempts());
              assets.forEach(asset -> store.markFailed(asset, "TRANSCODE_FAILED"));
              return assets;
            });
    if (buried != null) {
      dead.get(DeadReason.ABANDONED).increment(buried.size()); // counted once committed
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

  private Outcome fail(Job job, String workerId, RuntimeException e) {
    boolean permanent = e instanceof MediaToolException m && m.permanent();
    String error = e.getClass().getSimpleName() + ": " + e.getMessage();
    if (permanent || job.attempts() >= props.maxAttempts()) {
      log.error("job {} ({} {}) is DEAD: {}", job.id(), job.type(), job.rung(), error, e);
      Boolean buried =
          tx.execute(
              status -> {
                if (!jobs.bury(job.id(), workerId, error)) {
                  return false; // lost the lease: the rerun owns the job now
                }
                store.markFailed(
                    job.assetId(), permanent ? "MEDIA_UNREADABLE" : "TRANSCODE_FAILED");
                return true;
              });
      if (Boolean.TRUE.equals(buried)) { // counted once committed
        dead.get(permanent ? DeadReason.PERMANENT : DeadReason.ATTEMPTS).increment();
      }
      return Outcome.DEAD;
    }
    Duration delay = backoff(job.attempts());
    log.warn(
        "job {} failed (attempt {}), retrying in {}: {}", job.id(), job.attempts(), delay, error);
    jobs.reschedule(job.id(), workerId, clock.instant().plus(delay), error);
    return Outcome.RETRY;
  }

  /** 30 s, 2 m, 8 m, 32 m … ± 20 % jitter, so a storage outage doesn't retry every job at once. */
  Duration backoff(int attempt) {
    long base = props.baseBackoff().toMillis() * (1L << (2 * (attempt - 1)));
    double jitter = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
    return Duration.ofMillis((long) (base * jitter));
  }
}
