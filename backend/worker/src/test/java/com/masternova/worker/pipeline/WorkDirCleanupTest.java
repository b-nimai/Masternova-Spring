package com.masternova.worker.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.masternova.worker.pipeline.job.JobRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkDirCleanupTest {

  @TempDir Path root;

  @Test
  void keepsOnlyTheDirectoriesOfJobsRunningUnderALiveLease() throws Exception {
    UUID running = UUID.randomUUID();
    UUID crashed = UUID.randomUUID();
    Files.createDirectories(root.resolve(running + "/720p"));
    Files.writeString(
        Files.createDirectories(root.resolve(crashed + "/360p")).resolve("seg_00000.m4s"), "x");
    Files.createDirectories(root.resolve("not-a-job"));
    JobRepository jobs = mock(JobRepository.class);
    when(jobs.isLeased(running)).thenReturn(true); // ⭐ asked of the job table, not file times

    new WorkDirCleanup(TestPipelineProperties.withWorkDir(root), jobs).removeLeftovers();

    assertThat(root.resolve(running.toString())).exists();
    assertThat(root.resolve(crashed.toString())).doesNotExist();
    assertThat(root.resolve("not-a-job")).doesNotExist();
  }
}
