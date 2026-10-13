package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@link ProcessBuilder} traps, with real child processes ({@code sh}) instead of ffmpeg. */
class ProcessRunnerTest {

  @TempDir Path dir;
  private final ProcessRunner runner = new ProcessRunner();

  private static List<String> sh(String script) {
    return List.of("sh", "-c", script);
  }

  @Test
  void stdoutArrivesLineByLine() {
    List<String> lines = new ArrayList<>();
    var result = runner.run(sh("echo a; echo b"), dir, Duration.ofSeconds(5), lines::add);

    assertThat(lines).containsExactly("a", "b");
    assertThat(result.stdout()).isEqualTo("a\nb\n");
  }

  @Test
  void aProcessFloodingStderrDoesNotDeadlock() {
    // ⭐ 2 MB of stderr — far beyond a pipe buffer. Redirected to a file, so it can't block.
    runner.run(
        sh("head -c 2000000 /dev/zero | tr '\\0' x >&2"), dir, Duration.ofSeconds(10), l -> {});
  }

  @Test
  void aHangingProcessIsKilledAtTheTimeoutAsATemporaryFailure() {
    long start = System.nanoTime();
    assertThatThrownBy(() -> runner.run(sh("sleep 30"), dir, Duration.ofMillis(300), l -> {}))
        .isInstanceOfSatisfying(MediaToolException.class, e -> assertThat(e.permanent()).isFalse())
        .hasMessageContaining("timed out");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  void anUnreadableInputIsPermanent() {
    assertThatThrownBy(
            () ->
                runner.run(
                    sh("echo 'x.mp4: Invalid data found when processing input' >&2; exit 1"),
                    dir,
                    Duration.ofSeconds(5),
                    l -> {}))
        .isInstanceOfSatisfying(MediaToolException.class, e -> assertThat(e.permanent()).isTrue())
        .hasMessageContaining("Invalid data found");
  }

  @Test
  void anyOtherFailureIsTemporary() {
    assertThatThrownBy(
            () ->
                runner.run(
                    sh("echo 'Connection reset by peer' >&2; exit 1"),
                    dir,
                    Duration.ofSeconds(5),
                    l -> {}))
        .isInstanceOfSatisfying(MediaToolException.class, e -> assertThat(e.permanent()).isFalse());
  }
}
