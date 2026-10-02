package com.masternova.java.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourcesAndFinallyTest {

  @Test
  void resourcesCloseInReverseOrderEvenOnSuccess() {
    List<String> log = new ArrayList<>();

    try (var db = new TracingResource("db", log, false);
        var file = new TracingResource("file", log, false)) {
      db.use(false);
      file.use(false);
    }

    assertThat(log).containsExactly("open db", "open file", "use db", "use file", "close file", "close db");
  }

  @Test
  void aCloseFailureIsSuppressedBehindTheRealError() {
    List<String> log = new ArrayList<>();

    Throwable thrown =
        catchThrowable(
            () -> {
              try (var file = new TracingResource("file", log, true)) {
                file.use(true); // the REAL problem
              }
            });

    // ⭐ The body's exception wins; the close() failure is attached, not lost and not on top.
    assertThat(thrown).hasMessage("file failed while in use");
    assertThat(thrown.getSuppressed()).hasSize(1);
    assertThat(thrown.getSuppressed()[0]).hasMessage("file failed to close");
  }

  @Test
  void ifOnlyCloseFailsThatIsWhatYouGet() {
    Throwable thrown =
        catchThrowable(
            () -> {
              try (var file = new TracingResource("file", new ArrayList<>(), true)) {
                file.use(false);
              }
            });

    assertThat(thrown).hasMessage("file failed to close");
  }

  @Test
  void finallyRunsAfterTheReturnValueIsComputed() {
    List<String> log = new ArrayList<>();

    assertThat(FinallyBehaviour.finallyRunsAfterReturn(log)).isEqualTo("result");
    assertThat(log).containsExactly("try", "finally");
  }

  @Test
  void returnInFinallySilentlyDiscardsTheException() {
    // An IllegalStateException was thrown inside the method — and it's simply gone.
    assertThat(FinallyBehaviour.returnInFinallySwallowsTheException()).isEqualTo(42);
  }
}
