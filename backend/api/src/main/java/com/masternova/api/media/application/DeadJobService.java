package com.masternova.api.media.application;

import com.masternova.api.media.domain.MediaStatus;
import com.masternova.api.media.domain.PipelineJobs;
import com.masternova.api.media.domain.PipelineJobs.DeadJob;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pipeline's dead-letter queue, for admins: see what died and why, fix the cause, replay. A
 * replay puts the job back with a fresh attempt budget and moves the asset back out of FAILED; the
 * worker's poller picks it up like any other due job.
 */
@Service
public class DeadJobService {

  private final PipelineJobs jobs;
  private final Clock clock;

  DeadJobService(PipelineJobs jobs, Clock clock) {
    this.jobs = jobs;
    this.clock = clock;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<DeadJob> list() {
    return jobs.list(100);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public DeadJob replay(UUID jobId) {
    String status = jobs.statusOf(jobId).orElseThrow(() -> new NotFoundException("Job", jobId));
    if (!jobs.requeue(jobId, clock.instant())) {
      throw new ConflictException(
          "JOB_NOT_DEAD", "Only a dead job can be replayed.", Map.of("status", status));
    }
    DeadJob job = jobs.find(jobId).orElseThrow();
    // a dead PROBE means the asset never got past UPLOADED; any later job ran while PROCESSING
    jobs.reviveAsset(
        job.assetId(), job.type().equals("PROBE") ? MediaStatus.UPLOADED : MediaStatus.PROCESSING);
    return job;
  }
}
