package com.masternova.worker.pipeline.processor;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.ffmpeg.Ffprobe;
import com.masternova.worker.pipeline.ffmpeg.LadderProfile;
import com.masternova.worker.pipeline.ffmpeg.SourceInfo;
import com.masternova.worker.pipeline.job.Job;
import com.masternova.worker.pipeline.job.JobProcessor;
import com.masternova.worker.pipeline.job.JobSpec;
import com.masternova.worker.pipeline.job.JobType;
import com.masternova.worker.pipeline.job.ProgressSink;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * PROBE: what is this file? Its answer shapes the rest of the DAG — the ladder is chosen from the
 * source's height, so the TRANSCODE jobs can only be enqueued here (fan-out).
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
class ProbeProcessor extends JobProcessor<SourceInfo> {

  private final Ffprobe ffprobe;
  private final LadderProfile ladder;
  private final PipelineProperties props;

  ProbeProcessor(
      ObjectStorage storage, Ffprobe ffprobe, LadderProfile ladder, PipelineProperties props) {
    super(storage);
    this.ffprobe = ffprobe;
    this.ladder = ladder;
    this.props = props;
  }

  @Override
  public JobType type() {
    return JobType.PROBE;
  }

  @Override
  protected Work<SourceInfo> process(Job job, Path dir, ProgressSink progress) {
    var source =
        storage.presignInternalDownload(MediaKeys.source(job.assetId()), props.sourceUrlTtl());
    return new Work<>(List.of(), ffprobe.probe(source, dir));
  }

  @Override
  protected void record(Job job, SourceInfo info, PipelineStore store) {
    store.markProcessing(job.assetId(), info);
  }

  @Override
  protected List<JobSpec> next(Job job, SourceInfo info, PipelineStore store) {
    List<JobSpec> next = new ArrayList<>();
    ladder
        .rungsFor(info)
        .forEach(r -> next.add(new JobSpec(job.assetId(), JobType.TRANSCODE, r.name())));
    next.add(JobSpec.of(job.assetId(), JobType.THUMBNAILS));
    return next;
  }
}
