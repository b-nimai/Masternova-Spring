package com.masternova.java.exceptions;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/** Retries an action that may fail temporarily — e.g. a call to the payment provider. */
public final class Retry {

  private Retry() {}

  /**
   * Runs {@code action} up to {@code maxAttempts} times while it throws a {@code retryable}
   * exception, waiting {@code backoff × attempt} between tries. The last failure is rethrown
   * unchanged.
   *
   * @throws E whatever checked exception the action declares — the compiler knows its type
   */
  public static <T, E extends Exception> T withRetries(
      int maxAttempts,
      Duration backoff,
      Predicate<? super Exception> retryable,
      ThrowingSupplier<? extends T, E> action)
      throws E {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
    }
    Objects.requireNonNull(action, "action");

    for (int attempt = 1; ; attempt++) {
      try {
        return action.get();
      } catch (RuntimeException e) {
        if (attempt == maxAttempts || !retryable.test(e)) {
          throw e; // ⭐ rethrow the SAME exception: type, message and stack trace preserved
        }
      } catch (Exception e) {
        if (attempt == maxAttempts || !retryable.test(e)) {
          // action.get() declares only E, so any CHECKED exception caught here IS an E.
          // (`catch (E e)` is illegal — erasure, note 04 — hence the cast.)
          @SuppressWarnings("unchecked")
          E checked = (E) e;
          throw checked;
        }
      }
      sleep(backoff.multipliedBy(attempt));
    }
  }

  private static void sleep(Duration duration) {
    if (duration.isZero()) {
      return;
    }
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      // ⭐ NEVER swallow InterruptedException. Someone asked this thread to stop: restore the
      //    interrupt flag (catching it CLEARS the flag) and stop retrying.
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting to retry", e);
    }
  }
}
