package com.masternova.worker.pipeline;

import java.nio.file.Path;
import java.time.Duration;

/** {@link PipelineProperties} for unit tests: one slot, fast ladder, 2-minute lease. */
public final class TestPipelineProperties {

  private TestPipelineProperties() {}

  public static PipelineProperties defaults() {
    return withWorkDir(Path.of("/tmp/pipeline"));
  }

  public static PipelineProperties withWorkDir(Path workDir) {
    return new PipelineProperties(
        true,
        "fast",
        "ffmpeg",
        "ffprobe",
        workDir,
        1,
        Duration.ofSeconds(1),
        Duration.ofMinutes(2),
        Duration.ofSeconds(5),
        5,
        Duration.ofSeconds(30),
        Duration.ofHours(6));
  }
}
