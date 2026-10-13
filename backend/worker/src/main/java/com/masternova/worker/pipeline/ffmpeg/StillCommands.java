package com.masternova.worker.pipeline.ffmpeg;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * The poster and the scrub sprite: two short, fixed commands — a static factory each, not a builder
 * (nothing to configure).
 */
public final class StillCommands {

  /** Sprite tiles: up to 10 × 10 thumbnails, 160 px wide. */
  public static final int COLUMNS = 10;

  public static final int MAX_TILES = 100;
  public static final int TILE_WIDTH = 160;

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
        "scale=-2:'min(720,ih)'",
        "-q:v",
        "3",
        output.toString());
  }

  /** One thumbnail every {@code intervalSeconds}, tiled into a single JPEG. */
  public static List<String> sprite(
      String ffmpeg, URI input, double intervalSeconds, int rows, Path output) {
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
        "fps=1/"
            + format(intervalSeconds)
            + ",scale="
            + TILE_WIDTH
            + ":-2,tile="
            + COLUMNS
            + "x"
            + rows,
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
