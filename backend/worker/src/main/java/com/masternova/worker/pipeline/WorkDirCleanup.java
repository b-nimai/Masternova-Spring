package com.masternova.worker.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A SIGKILL skips the template's {@code finally}, so a job's work directory can outlive it. On
 * startup, remove job directories untouched for longer than a lease — a running job (another
 * process on the same host) keeps touching its own.
 */
@Component
class WorkDirCleanup {

  private static final Logger log = LoggerFactory.getLogger(WorkDirCleanup.class);

  private final PipelineProperties props;
  private final Clock clock;

  WorkDirCleanup(PipelineProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  void removeLeftovers() throws IOException {
    Path root = props.workDir();
    Files.createDirectories(root);
    FileTime cutoff = FileTime.from(clock.instant().minus(props.lease()));
    try (Stream<Path> dirs = Files.list(root)) {
      for (Path dir : dirs.toList()) {
        if (Files.getLastModifiedTime(dir).compareTo(cutoff) < 0) {
          log.info("removing a crashed job's work directory {}", dir);
          deleteTree(dir);
        }
      }
    }
  }

  private static void deleteTree(Path dir) throws IOException {
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
