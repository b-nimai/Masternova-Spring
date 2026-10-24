package com.masternova.patterns.bulkhead;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A Semaphore Bulkhead: at most {@code maxConcurrent} calls run at once; the next one waits up to
 * {@code maxWait} for a slot, then is REJECTED instead of joining an unbounded queue.
 *
 * <p>The real one is a servlet filter (api: {@code platform.overload.ConcurrencyLimitFilter}) that
 * answers a rejection with 503 OVERLOADED; here a rejection is the {@code fallback}.
 */
public final class Bulkhead {

  private final Semaphore permits;
  private final long maxWaitNanos;

  public Bulkhead(int maxConcurrent, Duration maxWait) {
    if (maxConcurrent < 1) {
      throw new IllegalArgumentException("maxConcurrent must be at least 1");
    }
    this.permits = new Semaphore(maxConcurrent);
    this.maxWaitNanos = maxWait.toNanos();
  }

  /** Runs {@code call} if a slot frees up in time, else returns {@code fallback} without running it. */
  public <T> T call(Supplier<T> call, Supplier<T> fallback) {
    boolean acquired;
    try {
      acquired = permits.tryAcquire(maxWaitNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      acquired = false;
    }
    if (!acquired) {
      return fallback.get();
    }
    try {
      return call.get();
    } finally {
      permits.release(); // ⭐ in finally: a call that throws gives its slot back
    }
  }

  public int available() {
    return permits.availablePermits();
  }
}
