package com.masternova.worker.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.worker.MutableClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkDirCleanupTest {

  @TempDir Path root;

  @Test
  void removesACrashedJobsDirectoryButNotARunningOne() throws Exception {
    MutableClock clock = new MutableClock();
    Path crashed = Files.createDirectories(root.resolve("crashed/360p"));
    Files.writeString(crashed.resolve("seg_00000.m4s"), "x");
    Files.setLastModifiedTime(
        root.resolve("crashed"), FileTime.from(clock.instant().minus(Duration.ofHours(1))));
    Path running = Files.createDirectories(root.resolve("running"));
    Files.setLastModifiedTime(running, FileTime.from(clock.instant()));

    var props = TestPipelineProperties.withWorkDir(root);
    new WorkDirCleanup(props, clock).removeLeftovers();

    assertThat(root.resolve("crashed")).doesNotExist();
    assertThat(running).exists();
  }
}
