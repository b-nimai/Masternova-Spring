package com.masternova.worker.pipeline.processor;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.storage.StorageUnavailableException;
import com.masternova.worker.pipeline.PipelineStore;
import com.masternova.worker.pipeline.PipelineStore.Rendition;
import com.masternova.worker.pipeline.job.Job;
import com.masternova.worker.pipeline.job.JobProcessor;
import com.masternova.worker.pipeline.job.JobSpec;
import com.masternova.worker.pipeline.job.JobType;
import com.masternova.worker.pipeline.job.ProgressSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * PACKAGE: the master playlist — the asset's COMMIT MARKER. Before writing it, every segment each
 * rung playlist lists is checked to exist in storage (a {@code HEAD} each), so {@code master.m3u8}
 * never points at something half-uploaded.
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
class PackageProcessor extends JobProcessor<Void> {

  private final PipelineStore store;
  private final Readiness readiness;

  PackageProcessor(ObjectStorage storage, PipelineStore store, Readiness readiness) {
    super(storage);
    this.store = store;
    this.readiness = readiness;
  }

  @Override
  public JobType type() {
    return JobType.PACKAGE;
  }

  @Override
  protected Work<Void> process(Job job, Path dir, ProgressSink progress) throws IOException {
    List<Rendition> renditions = store.renditions(job.assetId());
    if (renditions.isEmpty()) {
      throw new IllegalStateException("PACKAGE with no renditions: " + job);
    }
    boolean audio = store.sourceInfo(job.assetId()).orElseThrow().hasAudio();
    for (Rendition r : renditions) {
      String prefix = MediaKeys.rung(job.assetId(), r.rung());
      for (String file : referencedFiles(storage.getText(r.playlistKey()))) {
        if (storage.head(prefix + file).isEmpty()) {
          // temporary: the rung's rerun (or eventual consistency) will produce it
          throw new StorageUnavailableException("missing " + prefix + file, null);
        }
      }
    }
    Path master = Files.writeString(dir.resolve("master.m3u8"), masterPlaylist(renditions, audio));
    return new Work<>(
        List.of(
            Output.marker(
                master, MediaKeys.master(job.assetId()), "application/vnd.apple.mpegurl")),
        null);
  }

  @Override
  protected List<JobSpec> next(Job job, Void nothing, PipelineStore store) {
    readiness.readyIfComplete(job.assetId(), store);
    return List.of();
  }

  /** The media files a rung playlist points at: its {@code EXT-X-MAP} and every segment line. */
  static List<String> referencedFiles(String playlist) {
    return playlist
        .lines()
        .map(String::strip)
        .map(
            line ->
                line.startsWith("#EXT-X-MAP:")
                    ? line.replaceAll(".*URI=\"([^\"]+)\".*", "$1")
                    : line)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }

  /**
   * ADR-0013: one {@code EXT-X-STREAM-INF} per rung, lowest first. CODECS lets a player skip rungs
   * it can't decode without fetching them: H.264 High@4.0 ({@code avc1.640028}) + AAC-LC.
   */
  static String masterPlaylist(List<Rendition> renditions, boolean audio) {
    String codecs = audio ? "avc1.640028,mp4a.40.2" : "avc1.640028";
    StringBuilder m = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-INDEPENDENT-SEGMENTS\n");
    for (Rendition r : renditions) {
      m.append("#EXT-X-STREAM-INF:BANDWIDTH=")
          .append(r.bandwidth())
          .append(",RESOLUTION=")
          .append(r.width())
          .append('x')
          .append(r.height())
          .append(",CODECS=\"")
          .append(codecs)
          .append("\"\n")
          .append(r.rung())
          .append('/')
          .append(MediaKeys.PLAYLIST)
          .append('\n');
    }
    return m.toString();
  }
}
