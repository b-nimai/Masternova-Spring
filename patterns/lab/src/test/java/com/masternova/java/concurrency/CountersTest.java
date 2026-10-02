package com.masternova.java.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.masternova.java.concurrency.Counters.AdderCounter;
import com.masternova.java.concurrency.Counters.AtomicCounter;
import com.masternova.java.concurrency.Counters.Counter;
import com.masternova.java.concurrency.Counters.LockCounter;
import com.masternova.java.concurrency.Counters.SynchronizedCounter;
import com.masternova.java.concurrency.Counters.UnsafeCounter;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class CountersTest {

  private static final int THREADS = 8;
  private static final int PER_THREAD = 100_000;

  static Stream<Supplier<Counter>> safeCounters() {
    return Stream.of(SynchronizedCounter::new, AtomicCounter::new, AdderCounter::new, LockCounter::new);
  }

  @ParameterizedTest
  @MethodSource("safeCounters")
  void safeCountersNeverLoseAnUpdate(Supplier<Counter> factory) throws InterruptedException {
    Counter counter = factory.get();

    Race.run(THREADS, () -> {
      for (int i = 0; i < PER_THREAD; i++) {
        counter.increment();
      }
    });

    assertThat(counter.value()).isEqualTo((long) THREADS * PER_THREAD);
  }

  @Test
  void theUnsafeCounterLosesUpdates() throws InterruptedException {
    assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "needs real parallelism");

    // A race is probabilistic — retry a few rounds; on a multi-core machine the very first round
    // almost always loses thousands of increments.
    boolean lostAny = false;
    for (int round = 0; round < 20 && !lostAny; round++) {
      Counter counter = new UnsafeCounter();
      Race.run(THREADS, () -> {
        for (int i = 0; i < PER_THREAD; i++) {
          counter.increment();
        }
      });
      lostAny = counter.value() < (long) THREADS * PER_THREAD;
    }
    assertThat(lostAny).as("count++ from many threads should lose updates").isTrue();
  }
}
