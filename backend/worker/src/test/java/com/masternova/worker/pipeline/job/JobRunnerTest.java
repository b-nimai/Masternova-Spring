package com.masternova.worker.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masternova.storage.ObjectStorage;
import com.masternova.storage.StorageUnavailableException;
import com.masternova.worker.MutableClock;
import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.ffmpeg.MediaToolException;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The runner's outcomes — success, stale lease, retry with backoff, bury — over mocks, no DB. */
class JobRunnerTest {

  @TempDir Path workDir;
  final JobRepository jobs = mock(JobRepository.class);
  final PipelineStore store = mock(PipelineStore.class);
  final MutableClock clock = new MutableClock();
  final UUID asset = UUID.randomUUID();
  final ProgressPublisher publisher = mock(ProgressPublisher.class);
  final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  final ObservationRegistry observations = ObservationRegistry.create();

  {
    // what Boot wires in the app: every observation also becomes a timer
    observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
  }

  PipelineProperties props(Duration heartbeat) {
    return new PipelineProperties(
        true,
        "fast",
        "ffmpeg",
        "ffprobe",
        workDir,
        1,
        Duration.ofSeconds(1),
        Duration.ofMinutes(2),
        heartbeat,
        3,
        Duration.ofSeconds(30),
        Duration.ofHours(6));
  }

  /** A registry whose PROBE processor does {@code work}; every other type is a no-op. */
  JobRunner runner(Supplier<Void> work, Duration heartbeat) {
    List<JobProcessor<?>> processors =
        Arrays.stream(JobType.values())
            .<JobProcessor<?>>map(
                type ->
                    new JobProcessor<Void>(mock(ObjectStorage.class)) {
                      @Override
                      public JobType type() {
                        return type;
                      }

                      @Override
                      protected Work<Void> process(Job job, Path dir, ProgressSink progress) {
                        progress.report(42);
                        if (type == JobType.PROBE) {
                          work.get();
                        }
                        return new Work<>(List.of(), null);
                      }

                      @Override
                      protected List<JobSpec> next(Job job, Void r, PipelineStore s) {
                        return List.of(JobSpec.of(job.assetId(), JobType.THUMBNAILS));
                      }
                    })
            .toList();
    return new JobRunner(
        new JobProcessorRegistry(processors),
        jobs,
        store,
        new TransactionTemplate(mock(PlatformTransactionManager.class)),
        props(heartbeat),
        clock,
        publisher,
        observations);
  }

  /** How many {@code pipeline.job} runs ended with this outcome. */
  long runs(String outcome) {
    var timer = meters.find("pipeline.job").tag("outcome", outcome).timer();
    return timer == null ? 0 : timer.count();
  }

  Job probe(int attempt) {
    return new Job(UUID.randomUUID(), asset, JobType.PROBE, "", attempt);
  }

  @Test
  void successLocksTheAssetMarksDoneAndEnqueuesWhatItUnlocked() {
    when(jobs.complete(any(), eq("w"))).thenReturn(true);
    Job job = probe(1);

    runner(() -> null, Duration.ofMinutes(1)).execute(job, "w");

    verify(store).lockAsset(asset);
    verify(jobs).complete(job.id(), "w");
    verify(jobs).enqueue(JobSpec.of(asset, JobType.THUMBNAILS));
    verify(publisher).progress(job, 42); // live tick
    verify(publisher).changed(job); // after the commit
    assertThat(
            meters.get("pipeline.job").tags("job.type", "PROBE", "outcome", "done").timer().count())
        .isEqualTo(1);
  }

  @Test
  void aWorkerThatLostItsLeaseChangesNothing() {
    when(jobs.complete(any(), anyString())).thenReturn(false); // someone else reclaimed it

    runner(() -> null, Duration.ofMinutes(1)).execute(probe(1), "w");

    verify(jobs, never()).enqueue(any());
    assertThat(runs("lost_lease")).isEqualTo(1);
  }

  @Test
  void aTemporaryFailureIsRescheduledWithBackoff() {
    Job job = probe(2);

    runner(
            () -> {
              throw new StorageUnavailableException("503", null);
            },
            Duration.ofMinutes(1))
        .execute(job, "w");

    ArgumentCaptor<Instant> runAfter = ArgumentCaptor.forClass(Instant.class);
    verify(jobs).reschedule(eq(job.id()), eq("w"), runAfter.capture(), anyString());
    Duration delay = Duration.between(clock.instant(), runAfter.getValue());
    assertThat(delay).isBetween(Duration.ofSeconds(96), Duration.ofSeconds(144)); // 2 m ± 20 %
    verify(jobs, never()).bury(any(), any(), any());
    assertThat(
            meters
                .get("pipeline.job")
                .tags("outcome", "retry", "error", "StorageUnavailableException")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void aPermanentFailureIsBuriedAtOnceAndFailsTheAsset() {
    when(jobs.bury(any(), anyString(), anyString())).thenReturn(true);

    runner(
            () -> {
              throw new MediaToolException("Invalid data found", true);
            },
            Duration.ofMinutes(1))
        .execute(probe(1), "w");

    verify(store).markFailed(asset, "MEDIA_UNREADABLE");
    verify(jobs, never()).reschedule(any(), any(), any(), any());
    verify(publisher).changed(any()); // a failure is a change too
    assertThat(runs("dead")).isEqualTo(1);
  }

  @Test
  void theLastAttemptIsBuriedAsTranscodeFailed() {
    when(jobs.bury(any(), anyString(), anyString())).thenReturn(true);

    runner(
            () -> {
              throw new StorageUnavailableException("still down", null);
            },
            Duration.ofMinutes(1))
        .execute(probe(3), "w"); // max-attempts = 3

    verify(store).markFailed(asset, "TRANSCODE_FAILED");
  }

  @Test
  void aBuryThatLostTheLeaseDoesNotTouchTheAsset() {
    when(jobs.bury(any(), anyString(), anyString())).thenReturn(false);

    runner(
            () -> {
              throw new MediaToolException("x", true);
            },
            Duration.ofMinutes(1))
        .execute(probe(1), "w");

    verify(store, never()).markFailed(any(), any());
  }

  @Test
  void aLongJobHeartbeatsItsLeaseWithItsProgress() {
    when(jobs.heartbeat(any(), anyString(), any(), any())).thenReturn(true);
    when(jobs.complete(any(), anyString())).thenReturn(true);

    runner(
            () -> {
              sleep(150);
              return null;
            },
            Duration.ofMillis(20))
        .execute(probe(1), "w");

    verify(jobs, atLeastOnce()).heartbeat(any(), eq("w"), eq(Duration.ofMinutes(2)), eq(42));
  }

  @Test
  void aHeartbeatThatFindsTheLeaseGoneStops() {
    when(jobs.heartbeat(any(), anyString(), any(), any())).thenReturn(false);
    when(jobs.complete(any(), anyString())).thenReturn(false);

    runner(
            () -> {
              sleep(100);
              return null;
            },
            Duration.ofMillis(10))
        .execute(probe(1), "w");

    verify(jobs, atLeastOnce()).heartbeat(any(), any(), any(), any());
  }

  @Test
  void aJobFailingWhileThisProcessShutsDownIsHandedBackUncounted() {
    Job job = probe(2);
    JobRunner runner =
        runner(
            () -> {
              throw new StorageUnavailableException("upload aborted by the shutdown", null);
            },
            Duration.ofMinutes(1));
    runner.shutDown(); // ⭐ any step, any exception: it's the deploy, not the media

    runner.execute(job, "w");

    verify(jobs).release(job.id(), "w");
    verify(jobs, never()).reschedule(any(), any(), any(), any());
    verify(jobs, never()).bury(any(), any(), any());
    assertThat(runs("released")).isEqualTo(1);
  }

  @Test
  void abandonedJobsAreBuriedAndTheirAssetsFailedTogether() {
    UUID other = UUID.randomUUID();
    when(jobs.buryAbandoned(3)).thenReturn(List.of(asset, other));

    runner(() -> null, Duration.ofMinutes(1)).buryAbandoned();

    verify(store).markFailed(asset, "TRANSCODE_FAILED");
    verify(store).markFailed(other, "TRANSCODE_FAILED");
  }

  @Test
  void backoffQuadruplesPerAttempt() {
    JobRunner runner = runner(() -> null, Duration.ofMinutes(1));
    assertThat(runner.backoff(1)).isBetween(Duration.ofSeconds(24), Duration.ofSeconds(36));
    assertThat(runner.backoff(3))
        .isBetween(Duration.ofMinutes(6).plusSeconds(24), Duration.ofMinutes(9).plusSeconds(36));
  }

  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
