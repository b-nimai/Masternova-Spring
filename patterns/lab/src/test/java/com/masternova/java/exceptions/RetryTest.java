package com.masternova.java.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

  @Test
  void succeedsAfterTemporaryFailures() throws IOException {
    AtomicInteger calls = new AtomicInteger();

    String result =
        Retry.withRetries(3, Duration.ZERO, e -> e instanceof SocketTimeoutException, () -> {
          if (calls.incrementAndGet() < 3) {
            throw new SocketTimeoutException("bank slow"); // a CHECKED IOException
          }
          return "pay_123";
        });

    assertThat(result).isEqualTo("pay_123");
    assertThat(calls).hasValue(3);
  }

  @Test
  void givesUpAfterMaxAttemptsAndRethrowsTheSameException() {
    AtomicInteger calls = new AtomicInteger();

    assertThatThrownBy(
            () -> Retry.withRetries(2, Duration.ZERO, e -> true, () -> {
              calls.incrementAndGet();
              throw new SocketTimeoutException("still slow");
            }))
        .isInstanceOf(SocketTimeoutException.class)
        .hasMessage("still slow");
    assertThat(calls).hasValue(2);
  }

  @Test
  void doesNotRetryPermanentFailures() {
    AtomicInteger calls = new AtomicInteger();

    assertThatThrownBy(
            () -> Retry.withRetries(5, Duration.ZERO, e -> e instanceof SocketTimeoutException, () -> {
              calls.incrementAndGet();
              throw new IllegalArgumentException("card declined");
            }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void interruptionStopsRetryingAndKeepsTheFlag() throws Exception {
    Thread.currentThread().interrupt(); // someone asked this thread to stop

    try {
      assertThatThrownBy(
              () -> Retry.withRetries(3, Duration.ofMillis(50), e -> true, () -> {
                throw new IOException("down");
              }))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue(); // ⭐ flag restored
    } finally {
      Thread.interrupted(); // clear it so other tests aren't affected
    }
  }
}
