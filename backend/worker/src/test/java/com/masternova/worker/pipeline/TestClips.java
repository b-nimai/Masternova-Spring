package com.masternova.worker.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates real video files with ffmpeg's built-in test sources ({@code testsrc2} + a sine tone),
 * so the pipeline's tests transcode actual H.264/AAC instead of fixtures checked into git.
 */
public final class TestClips {

  private TestClips() {}

  /** An MP4 of {@code seconds} at {@code width}×{@code height}, 30 fps, with or without audio. */
  public static Path mp4(Path dir, int seconds, int width, int height, boolean audio)
      throws IOException, InterruptedException {
    Path out = dir.resolve("clip-" + width + "x" + height + "-" + seconds + "s.mp4");
    var args =
        new java.util.ArrayList<>(
            List.of(
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "testsrc2=size=" + width + "x" + height + ":rate=30"));
    if (audio) {
      args.addAll(List.of("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000"));
    }
    args.addAll(
        List.of("-t", Integer.toString(seconds), "-c:v", "libx264", "-preset", "ultrafast"));
    if (audio) {
      args.addAll(List.of("-c:a", "aac", "-shortest"));
    }
    args.add(out.toString());
    Process p = new ProcessBuilder(args).inheritIO().start();
    if (p.waitFor() != 0) {
      throw new IllegalStateException("ffmpeg could not generate a test clip");
    }
    return out;
  }
}
