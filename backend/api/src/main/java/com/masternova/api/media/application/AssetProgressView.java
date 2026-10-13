package com.masternova.api.media.application;

import com.masternova.api.media.domain.MediaStatus;
import com.masternova.api.media.domain.PipelineJobs.JobProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where an asset is: its status and every pipeline job's progress — the upload page's whole
 * "processing" panel. {@code percent} is the mean of the jobs that exist so far, held below 100
 * until the asset really is READY.
 */
public record AssetProgressView(
    UUID assetId,
    MediaStatus status,
    Optional<String> failureCode,
    int percent,
    List<JobProgress> jobs) {

  public boolean terminal() {
    return status == MediaStatus.READY || status == MediaStatus.FAILED;
  }

  static int overall(MediaStatus status, List<JobProgress> jobs) {
    if (status == MediaStatus.READY) {
      return 100;
    }
    if (jobs.isEmpty()) {
      return 0;
    }
    int mean = (int) jobs.stream().mapToInt(JobProgress::percent).average().orElse(0);
    return Math.min(mean, 99);
  }
}
