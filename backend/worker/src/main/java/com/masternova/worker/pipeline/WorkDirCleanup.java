package com.masternova.worker.pipeline;

import com.masternova.worker.pipeline.job.JobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A SIGKILL skips the template's {@code finally}, so a job's work directory can outlive it. On
 * startup, remove every job directory whose job isn't RUNNING under a live lease. ⭐ Directories are
 * named by job id, so this asks the job table — exact, unlike guessing from file times (a second
 * worker on the same host keeps its running job's files).
 */
@Component
class WorkDirCleanup {

  private static final Logger log = LoggerFactory.getLogger(WorkDirCleanup.class);

  private final PipelineProperties props;
  private final JobRepository jobs;

  WorkDirCleanup(PipelineProperties props, JobRepository jobs) {
    this.props = props;
    this.jobs = jobs;
  }

  @EventListener(ApplicationReadyEvent.class)
  void removeLeftovers() throws IOException {
    Path root = props.workDir();
    Files.createDirectories(root);
    try (Stream<Path> dirs = Files.list(root)) {
      for (Path dir : dirs.toList()) {
        if (!isLiveJob(dir)) {
          log.info("removing a crashed job's work directory {}", dir);
          WorkDirs.deleteTree(dir);
        }
      }
    }
  }

  private boolean isLiveJob(Path dir) {
    try {
      return jobs.isLeased(UUID.fromString(dir.getFileName().toString()));
    } catch (IllegalArgumentException notAJobId) {
      return false; // not ours to keep
    }
  }
}
