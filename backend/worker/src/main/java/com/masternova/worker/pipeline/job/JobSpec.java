package com.masternova.worker.pipeline.job;

import java.util.UUID;

/** A job to enqueue. {@code rung} is {@code ""} for the jobs that have none. */
public record JobSpec(UUID assetId, JobType type, String rung) {

  public static JobSpec of(UUID assetId, JobType type) {
    return new JobSpec(assetId, type, "");
  }
}
