package com.masternova.worker.pipeline.job;

import com.masternova.worker.pipeline.PipelineProperties;
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
 */
@Component
@ConditionalOnBooleanProperty(name = "masternova.pipeline.enabled", matchIfMissing = true)
class JobPoller implements AutoCloseable {

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

  /** On shutdown running ffmpeg children are interrupted (killed); their leases lapse → rerun. */
  @Override
  public void close() {
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
