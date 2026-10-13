package com.masternova.worker.pipeline.processor;

import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.job.JobRepository;
import com.masternova.worker.pipeline.job.JobRepository.JobState;
import com.masternova.worker.pipeline.job.JobType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The DAG's last fan-in: PACKAGE and THUMBNAILS finish in either order; whichever finishes second
 * (under the asset's row lock) marks the asset READY.
 */
@Component
class Readiness {

  private final JobRepository jobs;

  Readiness(JobRepository jobs) {
    this.jobs = jobs;
  }

  void readyIfComplete(UUID assetId, PipelineStore store) {
    List<JobState> all = jobs.jobsOf(assetId);
    boolean packaged = all.stream().anyMatch(j -> j.type() == JobType.PACKAGE && j.done());
    if (packaged && all.stream().allMatch(JobState::done)) {
      store.markReady(assetId);
    }
  }
}
