package com.masternova.worker.pipeline;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.worker.pipeline.job.JobRepository;
import com.masternova.worker.pipeline.job.JobSpec;
import com.masternova.worker.pipeline.job.JobType;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * media.uploaded.v1 → the DAG's first node. ⭐ Only ENQUEUES: running ffmpeg here would hold the
 * outbox's 60 s lease for minutes (video-pipeline LLD §7). A redelivered event enqueues nothing new
 * (the job's unique key).
 */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Observer", note = "patterns/docs/07-observer.md")
class MediaUploadedHandler implements OutboxHandler {

  /** The consumer's view of media's {@code MediaUploaded}: only what the pipeline needs. */
  record MediaUploadedView(String assetId) {}

  private final JobRepository jobs;
  private final JsonMapper json;

  MediaUploadedHandler(JobRepository jobs, JsonMapper json) {
    this.jobs = jobs;
    this.json = json;
  }

  @Override
  public String eventType() {
    return "media.uploaded.v1";
  }

  @Override
  public void handle(OutboxMessage message) {
    var event = json.readValue(message.payload(), MediaUploadedView.class);
    jobs.enqueue(JobSpec.of(UUID.fromString(event.assetId()), JobType.PROBE));
  }
}
