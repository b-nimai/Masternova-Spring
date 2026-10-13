package com.masternova.patterns.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The second shape of Builder in the project: not an immutable OBJECT but an argument LIST for a
 * child process (production: {@code com.masternova.worker.pipeline.ffmpeg.FfmpegCommand}).
 *
 * <ul>
 *   <li>required parts in the entry point ({@link #of}); optional ones fluent, with defaults;
 *   <li>{@link #build()} owns the ORDER (input options before {@code -i}, the output last) and
 *       the cross-field rule (a segment is a whole number of GOPs);
 *   <li>the result is a {@code List<String>}: each value is one argument, never shell-parsed.
 * </ul>
 */
public final class TranscodeArgs {

  private final String input;
  private final String output;
  private int height = 720;
  private int segmentSeconds = 6;
  private int gopSeconds = 2;
  private boolean audio = true;

  private TranscodeArgs(String input, String output) {
    this.input = Objects.requireNonNull(input);
    this.output = Objects.requireNonNull(output);
  }

  public static TranscodeArgs of(String input, String output) {
    return new TranscodeArgs(input, output);
  }

  public TranscodeArgs height(int height) {
    this.height = height;
    return this;
  }

  public TranscodeArgs segments(int segmentSeconds, int gopSeconds) {
    this.segmentSeconds = segmentSeconds;
    this.gopSeconds = gopSeconds;
    return this;
  }

  public TranscodeArgs withoutAudio() {
    this.audio = false;
    return this;
  }

  public List<String> build() {
    if (gopSeconds <= 0 || segmentSeconds % gopSeconds != 0) {
      throw new IllegalStateException("a segment must be a whole number of GOPs");
    }
    List<String> args = new ArrayList<>(List.of("ffmpeg", "-i", input, "-map", "0:v:0"));
    if (audio) {
      args.addAll(List.of("-map", "0:a:0", "-c:a", "aac"));
    }
    args.addAll(List.of("-vf", "scale=-2:" + height));
    args.addAll(List.of("-force_key_frames", "expr:gte(t,n_forced*" + gopSeconds + ")"));
    args.addAll(List.of("-hls_time", Integer.toString(segmentSeconds), output));
    return List.copyOf(args);
  }
}
