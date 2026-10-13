package com.masternova.api.media.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The api's window onto the worker's {@code pipeline_job} table: how far an asset's jobs are (the
 * progress stream, 7.7), what died, and putting a dead job back in the queue (video-pipeline LLD
 * §8). The worker owns every other write to the table.
 */
public interface PipelineJobs {

  record DeadJob(
      UUID id,
      UUID assetId,
      String type,
      String rung,
      int attempts,
      String lastError,
      Instant at) {}

  /** One job of an asset, as the upload page shows it. */
  record JobProgress(String type, String rung, String status, int percent) {}

  /** Every job of the asset, in DAG order (PROBE, TRANSCODE by rung, THUMBNAILS, PACKAGE). */
  List<JobProgress> progressOf(UUID assetId);

  List<DeadJob> list(int limit);

  /** The job and its status, whatever it is (replay must tell "not dead" from "not there"). */
  Optional<String> statusOf(UUID jobId);

  Optional<DeadJob> find(UUID jobId);

  /**
   * DEAD → PENDING with a fresh attempt budget, due now. {@code false} if it wasn't DEAD (a
   * concurrent replay won).
   */
  boolean requeue(UUID jobId, Instant now);

  /** FAILED → back to the status the replayed job needs, with the failure code cleared. */
  void reviveAsset(UUID assetId, MediaStatus to);
}
