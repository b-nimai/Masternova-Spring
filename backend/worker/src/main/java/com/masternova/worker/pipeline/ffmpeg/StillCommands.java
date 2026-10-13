package com.masternova.worker.pipeline.ffmpeg;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * The poster and the scrub sprite: two short, fixed commands — a static factory each, not a builder
 * (nothing to configure).
 *
 * <p>⭐ Both convert with {@link #JPEG_RANGE}: JPEG is FULL-range YUV, video is usually limited
 * range, and some ffmpeg builds (Alpine's 8.1, in the worker image) refuse to encode a JPEG from
 * limited-range frames ("Non full-range YUV is non-standard") where others (Homebrew's 9.0) convert
 * silently. Found by the e2e upload test; the conversion is now explicit.
 */
public final class StillCommands {

  /** Sprite tiles: up to 10 × 10 thumbnails, 160 px wide. */
  public static final int COLUMNS = 10;

  public static final int MAX_TILES = 100;
  public static final int TILE_WIDTH = 160;

  /** Convert to full-range 4:2:0 before the JPEG encoder (see the class comment). */
  static final String JPEG_RANGE = ":out_range=full,format=yuvj420p";

  private StillCommands() {}

  /** One frame at {@code atSeconds}, 720 px high (or the source's height if lower). */
  public static List<String> poster(String ffmpeg, URI input, double atSeconds, Path output) {
    return List.of(
        ffmpeg,
        "-nostdin",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-ss",
        format(atSeconds), // ⭐ before -i: an input seek (reads from there, doesn't decode up to it)
        "-i",
        input.toString(),
        "-frames:v",
        "1",
        "-vf",
        "scale=-2:'min(720,ih)'" + JPEG_RANGE,
        "-q:v",
        "3",
        output.toString());
  }

  /** One thumbnail every {@code intervalSeconds}, tiled into a single JPEG. */
  public static List<String> sprite(
      String ffmpeg, URI input, double intervalSeconds, int rows, Path output) {
    // ⭐ select: the FIRST frame, then one every interval. `fps=1/10` emits nothing at all for a
    //    clip shorter than the interval on some ffmpeg builds (Alpine's 8.1; the e2e test found it)
    String every =
        "select='isnan(prev_selected_t)+gte(t-prev_selected_t\\," + format(intervalSeconds) + ")'";
    String filter =
        every + ",scale=" + TILE_WIDTH + ":-2" + JPEG_RANGE + ",tile=" + COLUMNS + "x" + rows;
    return List.of(
        ffmpeg,
        "-nostdin",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-i",
        input.toString(),
        "-vf",
        filter,
        "-fps_mode",
        "vfr", // only the selected frames, not duplicates to fill a constant rate
        "-frames:v",
        "1",
        "-q:v",
        "5",
        output.toString());
  }

  /** Seconds between sprite thumbnails: 10 s, or longer so at most {@link #MAX_TILES} fit. */
  public static double spriteInterval(double durationSeconds) {
    return Math.max(10, Math.ceil(durationSeconds / MAX_TILES));
  }

  private static String format(double seconds) {
    return String.format(java.util.Locale.ROOT, "%.3f", seconds);
  }
}
