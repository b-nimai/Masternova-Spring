package com.masternova.worker.pipeline.job;

import com.masternova.worker.pipeline.PipelineProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Claims due jobs into FREE slots, one virtual thread per job. ⭐ Claims only what it can run now
 * ({@code slots} permits): a claimed-but-queued job would hold a lease it can't renew while it
 * waits — no different from a dead worker to everyone else.
 *
 * <p>It also reports its own saturation (busy slots of all slots, USE) and the queue it drains —
 * the two numbers that say "add workers".
 */
@Component
@ConditionalOnBooleanProperty(name = "masternova.pipeline.enabled", matchIfMissing = true)
class JobPoller implements AutoCloseable, MeterBinder {

  private final JobRepository jobs;
  private final JobRunner runner;
  private final PipelineProperties props;
  private final Semaphore slots;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final String workerId = workerId();

  JobPoller(JobRepository jobs, JobRunner runner, PipelineProperties props) {
    this.jobs = jobs;
    this.runner = runner;
    this.props = props;
    this.slots = new Semaphore(props.slots());
  }

  @Scheduled(
      fixedDelayString = "${masternova.pipeline.poll-interval:2s}",
      initialDelayString = "${masternova.pipeline.poll-interval:2s}")
  void poll() {
    runner.buryAbandoned();
    int free = slots.availablePermits();
    if (free == 0) {
      return;
    }
    for (Job job : jobs.claim(free, props.lease(), workerId)) {
      slots.acquireUninterruptibly();
      executor.submit(
          () -> {
            try {
              runner.execute(job, workerId);
            } finally {
              slots.release();
            }
          });
    }
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("pipeline.slots.busy", slots, s -> props.slots() - s.availablePermits())
        .description("Pipeline jobs running in this worker now")
        .register(registry);
    Gauge.builder("pipeline.slots", props, PipelineProperties::slots)
        .description("Pipeline jobs this worker may run at once")
        .register(registry);
    Gauge.builder("pipeline.jobs.queued", jobs, JobRepository::countDue)
        .description("Pipeline jobs due and waiting for a free slot (all workers)")
        .register(registry);
  }

  /**
   * On shutdown, running jobs are interrupted (their ffmpeg killed) and handed back uncounted
   * ({@code JobRunner}: {@code release}); another worker picks them up at once.
   */
  @Override
  public void close() {
    runner.shutDown(); // first: the interrupted jobs are handed back, not counted as failures
    executor.shutdownNow();
  }

  /** {@code host:pid:random} — readable in {@code pipeline_job.worker_id}, unique per process. */
  static String workerId() {
    String host;
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      host = "unknown";
    }
    String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    return host + ":" + pid + ":" + UUID.randomUUID().toString().substring(0, 8);
  }
}
