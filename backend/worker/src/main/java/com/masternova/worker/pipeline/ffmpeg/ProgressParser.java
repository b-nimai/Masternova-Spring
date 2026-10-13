package com.masternova.worker.pipeline.ffmpeg;

import java.util.OptionalInt;

/**
 * Reads ffmpeg's {@code -progress pipe:1} output — {@code key=value} lines, a block per second —
 * and turns {@code out_time_us} into a percentage of the source's duration.
 */
public final class ProgressParser {

  private final double durationSeconds;

  public ProgressParser(double durationSeconds) {
    if (durationSeconds <= 0) {
      throw new IllegalArgumentException("duration must be positive");
    }
    this.durationSeconds = durationSeconds;
  }

  /** The percentage a line reports, if it reports one; {@code progress=end} is 100. */
  public OptionalInt percent(String line) {
    if (line.equals("progress=end")) {
      return OptionalInt.of(100);
    }
    if (!line.startsWith("out_time_us=")) {
      return OptionalInt.empty();
    }
    String value = line.substring("out_time_us=".length()).strip();
    if (value.equals("N/A")) { // before the first frame is out
      return OptionalInt.empty();
    }
    double seconds = Long.parseLong(value) / 1_000_000.0;
    int percent = (int) Math.floor(100 * seconds / durationSeconds);
    return OptionalInt.of(Math.clamp(percent, 0, 99)); // 100 only when ffmpeg says it's done
  }
}
