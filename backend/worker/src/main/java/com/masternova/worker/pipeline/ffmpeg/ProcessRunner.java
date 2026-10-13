package com.masternova.worker.pipeline.ffmpeg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Runs ffmpeg / ffprobe as a child process — the parts of {@link ProcessBuilder} that bite:
 *
 * <ul>
 *   <li>⭐ stderr goes to a FILE, not a pipe. A pipe nobody drains fills its ~64 KB buffer, and
 *       ffmpeg then blocks forever writing to it — a deadlock that looks like a hang.
 *   <li>stdout is read line by line on a virtual thread (progress), while this thread waits with a
 *       TIMEOUT; on timeout the process is {@code destroyForcibly()}'d (SIGKILL — ffmpeg may ignore
 *       SIGTERM mid-encode).
 *   <li>failures are classified: an input ffmpeg can't read is PERMANENT; anything else (a storage
 *       hiccup mid-read, a timeout) is temporary.
 * </ul>
 */
@Component
public class ProcessRunner {

  /** ffmpeg's messages that mean "this input will never work". */
  private static final List<String> PERMANENT =
      List.of(
          "Invalid data found when processing input",
          "does not contain any stream",
          "moov atom not found",
          "Output file does not contain any stream",
          "Stream map '0:v:0' matches no streams");

  public record Result(String stdout) {}

  /**
   * @param stdoutLines called for each stdout line as it arrives; also collected into the result
   * @param workDir where the stderr log goes (and the process's working directory)
   */
  public Result run(
      List<String> command, Path workDir, Duration timeout, Consumer<String> stdoutLines) {
    Path stderr = workDir.resolve("stderr-" + System.nanoTime() + ".log");
    Process process;
    try {
      process =
          new ProcessBuilder(command)
              .directory(workDir.toFile())
              .redirectError(stderr.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
              .start();
    } catch (IOException e) {
      throw new MediaToolException(
          "can't start " + command.getFirst() + ": " + e.getMessage(), false);
    }
    StringBuilder out = new StringBuilder();
    Thread reader =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (var lines =
                      new BufferedReader(
                          new InputStreamReader(
                              process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                      out.append(line).append('\n');
                      stdoutLines.accept(line);
                    }
                  } catch (IOException e) {
                    // the process was killed: the stream closes under us
                  }
                });
    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly().waitFor();
        throw new MediaToolException(command.getFirst() + " timed out after " + timeout, false);
      }
      reader.join();
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new JobInterruptedException(command.getFirst() + " was interrupted");
    }
    if (process.exitValue() != 0) {
      String tail = tail(stderr);
      boolean permanent = PERMANENT.stream().anyMatch(tail::contains);
      throw new MediaToolException(
          command.getFirst() + " exited " + process.exitValue() + ": " + tail, permanent);
    }
    deleteQuietly(stderr);
    return new Result(out.toString());
  }

  /** The last 4 KB of stderr — what ends up in {@code pipeline_job.last_error}. */
  private static String tail(Path file) {
    try {
      String all = Files.readString(file, StandardCharsets.UTF_8);
      return all.length() <= 4096 ? all.strip() : all.substring(all.length() - 4096).strip();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // the job's work directory is removed afterwards anyway
    }
  }

  private static java.io.File nullDevice() {
    return new java.io.File(
        System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
  }
}
