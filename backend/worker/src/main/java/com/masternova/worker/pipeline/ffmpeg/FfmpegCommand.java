package com.masternova.worker.pipeline.ffmpeg;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.storage.MediaKeys;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BUILDER for one ffmpeg HLS rendition: ~40 arguments whose ORDER matters (input options before
 * {@code -i}, output options before the output) and whose values constrain each other.
 *
 * <ul>
 *   <li>⭐ It builds a {@code List<String>} for {@link ProcessBuilder} — never a shell string, so
 *       nothing is re-parsed: no quoting bugs, no injection through a url or a path.
 *   <li>Required parts come in the entry point ({@link #hls}); optional ones are fluent; {@link
 *       #build()} checks the cross-field rules (a segment must be a whole number of GOPs —
 *       ADR-0013's "every segment boundary is a switch point").
 * </ul>
 *
 * <pre>{@code
 * FfmpegCommand.hls(ffmpeg, sourceUrl, rungDir).rung(Rung.of(720, 2800, 128), "veryfast")
 *     .withAudio(true).build();
 * }</pre>
 */
@DesignPattern(value = Pattern.BUILDER, role = "Builder", note = "patterns/docs/10-builder.md")
public final class FfmpegCommand {

  public static final Duration DEFAULT_SEGMENT = Duration.ofSeconds(6);
  public static final Duration DEFAULT_GOP = Duration.ofSeconds(2);

  private final String ffmpeg;
  private final URI input;
  private final Path outputDir;
  private Rung rung;
  private String preset = "veryfast";
  private boolean audio = true;
  private Duration segment = DEFAULT_SEGMENT;
  private Duration gop = DEFAULT_GOP;
  private boolean progress = true;

  private FfmpegCommand(String ffmpeg, URI input, Path outputDir) {
    this.ffmpeg = Objects.requireNonNull(ffmpeg);
    this.input = Objects.requireNonNull(input);
    this.outputDir = Objects.requireNonNull(outputDir);
  }

  /** The required parts: which binary, what to read, which directory gets the rendition. */
  public static FfmpegCommand hls(String ffmpeg, URI input, Path outputDir) {
    return new FfmpegCommand(ffmpeg, input, outputDir);
  }

  public FfmpegCommand rung(Rung rung, String preset) {
    this.rung = Objects.requireNonNull(rung);
    this.preset = Objects.requireNonNull(preset);
    return this;
  }

  /** Map the first audio stream; off for silent sources (ffprobe says). */
  public FfmpegCommand withAudio(boolean audio) {
    this.audio = audio;
    return this;
  }

  public FfmpegCommand segments(Duration segment, Duration gop) {
    this.segment = Objects.requireNonNull(segment);
    this.gop = Objects.requireNonNull(gop);
    return this;
  }

  /** {@code -progress pipe:1}: key=value progress lines on stdout ({@link ProgressParser}). */
  public FfmpegCommand withProgress(boolean progress) {
    this.progress = progress;
    return this;
  }

  public List<String> build() {
    if (rung == null) {
      throw new IllegalStateException("an HLS rendition needs a rung");
    }
    if (gop.isZero() || segment.toMillis() % gop.toMillis() != 0) {
      throw new IllegalStateException(
          "a segment (" + segment + ") must be a whole number of GOPs (" + gop + ")");
    }
    double gopSeconds = gop.toMillis() / 1000.0;
    List<String> args = new ArrayList<>();
    // global + input
    args.addAll(List.of(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y"));
    args.addAll(List.of("-i", input.toString()));
    // streams
    args.addAll(List.of("-map", "0:v:0"));
    if (audio) {
      args.addAll(List.of("-map", "0:a:0"));
    }
    // video: H.264 High, CBR-ish with a capped peak, a CLOSED GOP aligned across every rung
    args.addAll(List.of("-c:v", "libx264", "-preset", preset, "-profile:v", "high"));
    args.addAll(List.of("-pix_fmt", "yuv420p", "-vf", "scale=-2:" + rung.height()));
    args.addAll(
        List.of(
            "-b:v",
            rung.videoKbps() + "k",
            "-maxrate",
            rung.maxrateKbps() + "k",
            "-bufsize",
            (rung.videoKbps() * 3 / 2) + "k"));
    // ⭐ keyframes by TIME, not by frame count: every rung (and every fps) cuts at the same instants
    args.addAll(
        List.of(
            "-force_key_frames",
            "expr:gte(t,n_forced*" + gopSeconds + ")",
            "-sc_threshold",
            "0",
            "-flags",
            "+cgop"));
    if (audio) {
      args.addAll(
          List.of("-c:a", "aac", "-b:a", rung.audioKbps() + "k", "-ac", "2", "-ar", "48000"));
    }
    // HLS, fMP4 segments, names from MediaKeys so the uploaded keys are deterministic (7.10)
    args.addAll(
        List.of(
            "-f",
            "hls",
            "-hls_time",
            Long.toString(segment.toSeconds()),
            "-hls_playlist_type",
            "vod",
            "-hls_segment_type",
            "fmp4",
            "-hls_fmp4_init_filename",
            MediaKeys.INIT_SEGMENT,
            "-hls_segment_filename",
            outputDir.resolve(MediaKeys.SEGMENT_PATTERN).toString()));
    if (progress) {
      args.addAll(List.of("-progress", "pipe:1", "-stats_period", "1"));
    }
    args.add(outputDir.resolve(MediaKeys.PLAYLIST).toString());
    return List.copyOf(args);
  }
}
