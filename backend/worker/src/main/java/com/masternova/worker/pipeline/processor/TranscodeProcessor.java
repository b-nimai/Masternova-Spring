package com.masternova.worker.pipeline.processor;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.PipelineStore.Rendition;
import com.masternova.worker.pipeline.ffmpeg.FfmpegCommand;
import com.masternova.worker.pipeline.ffmpeg.LadderProfile;
import com.masternova.worker.pipeline.ffmpeg.ProcessRunner;
import com.masternova.worker.pipeline.ffmpeg.ProgressParser;
import com.masternova.worker.pipeline.ffmpeg.Rung;
import com.masternova.worker.pipeline.ffmpeg.SourceInfo;
import com.masternova.worker.pipeline.job.Job;
import com.masternova.worker.pipeline.job.JobProcessor;
import com.masternova.worker.pipeline.job.JobRepository;
import com.masternova.worker.pipeline.job.JobSpec;
import com.masternova.worker.pipeline.job.JobType;
import com.masternova.worker.pipeline.job.ProgressSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * TRANSCODE(rung): one HLS rendition. ffmpeg reads the source over a presigned url (range seeks, no
 * full download) and writes segments locally; the template uploads them with {@code index.m3u8}
 * last. When the LAST rung finishes, it enqueues PACKAGE (fan-in, under the asset's row lock).
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
class TranscodeProcessor extends JobProcessor<Rendition> {

  private final ProcessRunner runner;
  private final LadderProfile ladder;
  private final PipelineStore store;
  private final JobRepository jobs;
  private final PipelineProperties props;

  TranscodeProcessor(
      ObjectStorage storage,
      ProcessRunner runner,
      LadderProfile ladder,
      PipelineStore store,
      JobRepository jobs,
      PipelineProperties props) {
    super(storage);
    this.runner = runner;
    this.ladder = ladder;
    this.store = store;
    this.jobs = jobs;
    this.props = props;
  }

  @Override
  public JobType type() {
    return JobType.TRANSCODE;
  }

  @Override
  protected Work<Rendition> process(Job job, Path dir, ProgressSink progress) throws IOException {
    SourceInfo source =
        store
            .sourceInfo(job.assetId())
            .orElseThrow(() -> new IllegalStateException("TRANSCODE before PROBE: " + job));
    Rung rung =
        ladder.rungsFor(source).stream()
            .filter(r -> r.name().equals(job.rung()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("no rung " + job.rung() + " in the ladder"));
    Path out = Files.createDirectories(dir.resolve(rung.name()));
    var input =
        storage.presignInternalDownload(MediaKeys.source(job.assetId()), props.sourceUrlTtl());
    ProgressParser parser = new ProgressParser(source.durationSeconds());

    runner.run(
        FfmpegCommand.hls(props.ffmpegPath(), input, out)
            .rung(rung, ladder.preset())
            .withAudio(source.hasAudio())
            .build(),
        dir,
        timeout(source),
        line -> parser.percent(line).ifPresent(progress::report));

    List<Output> outputs = new ArrayList<>();
    int segments = 0;
    try (Stream<Path> files = Files.list(out)) {
      for (Path file : files.sorted().toList()) {
        String name = file.getFileName().toString();
        String key = MediaKeys.rung(job.assetId(), rung.name()) + name;
        if (name.equals(MediaKeys.PLAYLIST)) {
          outputs.add(Output.marker(file, key, "application/vnd.apple.mpegurl"));
        } else {
          outputs.add(
              Output.of(file, key, name.endsWith(".mp4") ? "video/mp4" : "video/iso.segment"));
          segments += name.endsWith(".m4s") ? 1 : 0;
        }
      }
    }
    int width = evenWidth(source, rung.height());
    return new Work<>(
        outputs,
        new Rendition(
            rung.name(),
            width,
            rung.height(),
            rung.bandwidth(),
            MediaKeys.rungPlaylist(job.assetId(), rung.name()),
            segments));
  }

  @Override
  protected void record(Job job, Rendition rendition, PipelineStore store) {
    store.upsertRendition(job.assetId(), rendition);
  }

  /** ⭐ Fan-in: the job that finds every TRANSCODE done enqueues PACKAGE (once: unique key). */
  @Override
  protected List<JobSpec> next(Job job, Rendition rendition, PipelineStore store) {
    boolean allRungsDone =
        jobs.jobsOf(job.assetId()).stream()
            .filter(j -> j.type() == JobType.TRANSCODE)
            .allMatch(JobRepository.JobState::done);
    return allRungsDone ? List.of(JobSpec.of(job.assetId(), JobType.PACKAGE)) : List.of();
  }

  /** ffmpeg's {@code scale=-2:h}: the width that keeps the aspect ratio, rounded to even. */
  static int evenWidth(SourceInfo source, int height) {
    int width = (int) Math.round((double) source.width() * height / source.height());
    return width - width % 2;
  }

  /** Generous: a slow encode is fine, a HUNG one isn't. */
  private static Duration timeout(SourceInfo source) {
    return Duration.ofSeconds(Math.max(600, (long) (3 * source.durationSeconds())));
  }
}
