package com.masternova.java.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CohortAndWebhookTest {

  @Test
  void casLoopNeverOversellsTheLastSeats() throws InterruptedException {
    Cohort cohort = new Cohort(100);
    AtomicInteger admitted = new AtomicInteger();

    Race.run(200, () -> {
      if (cohort.tryEnroll()) {
        admitted.incrementAndGet();
      }
    });

    assertThat(admitted).hasValue(100); // exactly the capacity — never 101
    assertThat(cohort.seatsTaken()).isEqualTo(100);
  }

  @Test
  void checkThenActOversells() throws InterruptedException {
    assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "needs real parallelism");

    boolean oversold = false;
    for (int round = 0; round < 50 && !oversold; round++) {
      Cohort cohort = new Cohort(10);
      AtomicInteger admitted = new AtomicInteger();
      Race.run(64, () -> {
        if (cohort.tryEnrollUnsafe()) {
          admitted.incrementAndGet();
        }
      });
      oversold = admitted.get() > 10; // more learners admitted than seats exist
    }
    assertThat(oversold).as("check-then-act should admit more than capacity").isTrue();
  }

  @Test
  void fiftyConcurrentCopiesOfOneWebhookEnrollExactlyOnce() throws InterruptedException {
    WebhookProcessor processor = new WebhookProcessor();
    AtomicInteger winners = new AtomicInteger();

    Race.run(50, () -> {
      if (processor.handle("evt_razorpay_123")) {
        winners.incrementAndGet();
      }
    });

    // ⭐ The Phase 9 proof, in miniature: 50 deliveries, 1 effect.
    assertThat(winners).hasValue(1);
    assertThat(processor.enrollments()).isEqualTo(1);
  }

  @Test
  void differentEventsAreEachProcessedOnce() {
    WebhookProcessor processor = new WebhookProcessor();

    processor.handle("evt_1");
    processor.handle("evt_2");
    processor.handle("evt_1");

    assertThat(processor.enrollments()).isEqualTo(2);
  }
}
