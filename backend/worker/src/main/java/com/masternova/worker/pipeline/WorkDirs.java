package com.masternova.worker.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Job work directories: removed whole, quietly — a leftover never fails a job. */
public final class WorkDirs {

  private WorkDirs() {}

  public static void deleteTree(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(WorkDirs::deleteQuietly);
    } catch (IOException e) {
      // the startup cleanup gets it next time
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // see deleteTree
    }
  }
}
