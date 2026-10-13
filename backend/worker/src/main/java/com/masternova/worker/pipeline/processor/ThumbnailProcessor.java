package com.masternova.worker.pipeline.processor;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.ffmpeg.ProcessRunner;
import com.masternova.worker.pipeline.ffmpeg.SourceInfo;
import com.masternova.worker.pipeline.ffmpeg.StillCommands;
import com.masternova.worker.pipeline.job.Job;
import com.masternova.worker.pipeline.job.JobProcessor;
import com.masternova.worker.pipeline.job.JobSpec;
import com.masternova.worker.pipeline.job.JobType;
import com.masternova.worker.pipeline.job.ProgressSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * THUMBNAILS: the poster, and the scrub sprite + its WebVTT index (a player shows the tile under
 * the cursor while seeking). Runs beside the rungs; it doesn't wait for them.
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
class ThumbnailProcessor extends JobProcessor<Void> {

  private final ProcessRunner runner;
  private final PipelineStore store;
  private final Readiness readiness;
  private final PipelineProperties props;

  ThumbnailProcessor(
      ObjectStorage storage,
      ProcessRunner runner,
      PipelineStore store,
      Readiness readiness,
      PipelineProperties props) {
    super(storage);
    this.runner = runner;
    this.store = store;
    this.readiness = readiness;
    this.props = props;
  }

  @Override
  public JobType type() {
    return JobType.THUMBNAILS;
  }

  @Override
  protected Work<Void> process(Job job, Path dir, ProgressSink progress) throws IOException {
    SourceInfo source = store.sourceInfo(job.assetId()).orElseThrow();
    var input =
        storage.presignInternalDownload(MediaKeys.source(job.assetId()), props.sourceUrlTtl());
    Path poster = dir.resolve(MediaKeys.POSTER);
    Path sprite = dir.resolve(MediaKeys.SPRITE);
    Path vtt = dir.resolve(MediaKeys.SPRITE_VTT);

    double interval = StillCommands.spriteInterval(source.durationSeconds());
    int tiles = (int) Math.max(1, Math.ceil(source.durationSeconds() / interval));
    int rows = Math.ceilDiv(tiles, StillCommands.COLUMNS);
    Duration timeout = Duration.ofMinutes(10);
    String ffmpeg = props.ffmpegPath();
    runner.run(
        StillCommands.poster(ffmpeg, input, Math.min(5, source.durationSeconds() / 3), poster),
        dir,
        timeout,
        l -> {});
    runner.run(StillCommands.sprite(ffmpeg, input, interval, rows, sprite), dir, timeout, l -> {});
    Files.writeString(vtt, spriteVtt(source, interval, tiles));

    return new Work<>(
        List.of(
            Output.of(poster, MediaKeys.poster(job.assetId()), "image/jpeg"),
            Output.of(sprite, MediaKeys.sprite(job.assetId()), "image/jpeg"),
            Output.of(vtt, MediaKeys.spriteVtt(job.assetId()), "text/vtt")),
        null);
  }

  @Override
  protected List<JobSpec> next(Job job, Void nothing, PipelineStore store) {
    readiness.readyIfComplete(job.assetId(), store);
    return List.of();
  }

  /**
   * One cue per tile: {@code sprite.jpg#xywh=x,y,w,h} — the format hls players' thumbnails read.
   */
  static String spriteVtt(SourceInfo source, double interval, int tiles) {
    int w = StillCommands.TILE_WIDTH;
    int h = (int) Math.round((double) w * source.height() / source.width());
    h -= h % 2;
    StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
    for (int i = 0; i < tiles; i++) {
      double start = i * interval;
      double end = Math.min(source.durationSeconds(), (i + 1) * interval);
      int x = (i % StillCommands.COLUMNS) * w;
      int y = (i / StillCommands.COLUMNS) * h;
      vtt.append(timestamp(start))
          .append(" --> ")
          .append(timestamp(end))
          .append('\n')
          .append(MediaKeys.SPRITE)
          .append("#xywh=")
          .append(x)
          .append(',')
          .append(y)
          .append(',')
          .append(w)
          .append(',')
          .append(h)
          .append("\n\n");
    }
    return vtt.toString();
  }

  private static String timestamp(double seconds) {
    long ms = Math.round(seconds * 1000);
    return String.format(
        Locale.ROOT,
        "%02d:%02d:%02d.%03d",
        ms / 3_600_000,
        ms / 60_000 % 60,
        ms / 1000 % 60,
        ms % 1000);
  }
}
