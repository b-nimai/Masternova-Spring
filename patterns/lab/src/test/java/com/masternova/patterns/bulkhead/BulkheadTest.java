package com.masternova.patterns.bulkhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BulkheadTest {

  @Test
  void past_the_limit_calls_are_rejected_not_queued() throws Exception {
    Bulkhead bulkhead = new Bulkhead(2, Duration.ofMillis(20));
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch bothInside = new CountDownLatch(2);
    AtomicInteger ran = new AtomicInteger();

    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<String>> holders = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        holders.add(
            pool.submit(
                () ->
                    bulkhead.call(
                        () -> {
                          bothInside.countDown();
                          await(release);
                          return "ok";
                        },
                        () -> "rejected")));
      }
      assertThat(bothInside.await(5, TimeUnit.SECONDS)).isTrue();

      // ⭐ the third call never runs: it gets the fallback after maxWait
      String third =
          bulkhead.call(
              () -> {
                ran.incrementAndGet();
                return "ok";
              },
              () -> "rejected");
      assertThat(third).isEqualTo("rejected");
      assertThat(ran).hasValue(0);

      release.countDown();
      for (Future<String> holder : holders) {
        assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
      }
    }
    assertThat(bulkhead.available()).isEqualTo(2);
  }

  @Test
  void a_throwing_call_gives_its_slot_back() {
    Bulkhead bulkhead = new Bulkhead(1, Duration.ZERO);
    assertThatThrownBy(
            () ->
                bulkhead.call(
                    () -> {
                      throw new IllegalStateException("boom");
                    },
                    () -> "rejected"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(bulkhead.available()).isEqualTo(1);
    assertThat(bulkhead.call(() -> "ok", () -> "rejected")).isEqualTo("ok");
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
