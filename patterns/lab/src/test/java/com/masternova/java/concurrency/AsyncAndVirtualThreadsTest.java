package com.masternova.java.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.concurrency.QuoteService.Provider;
import com.masternova.java.valueobject.Money;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AsyncAndVirtualThreadsTest {

  private static Provider provider(String name, long rupees, long delayMillis) {
    return new Provider(name, () -> {
      sleep(delayMillis);
      return Money.of(rupees * 100, "INR");
    });
  }

  @Test
  void callsRunInParallelSoTotalTimeIsTheSlowestNotTheSum() {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      QuoteService quotes = new QuoteService(executor, Duration.ofSeconds(2));
      List<Provider> providers =
          List.of(provider("a", 120, 300), provider("b", 100, 300), provider("c", 110, 300));

      long start = System.nanoTime();
      Money best = quotes.cheapest(providers, Money.zero("INR"));
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertThat(best).isEqualTo(Money.of(100_00, "INR"));
      assertThat(elapsedMs).isLessThan(800); // sequential would be ≥ 900 ms
    }
  }

  @Test
  void slowAndFailingProvidersAreSkipped() {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      QuoteService quotes = new QuoteService(executor, Duration.ofMillis(200));
      Provider broken = new Provider("broken", () -> {
        throw new IllegalStateException("503");
      });
      List<Provider> providers =
          List.of(provider("slow-but-cheap", 1, 2_000), broken, provider("ok", 150, 10));

      assertThat(quotes.cheapest(providers, Money.zero("INR"))).isEqualTo(Money.of(150_00, "INR"));
    }
  }

  @Test
  void allProvidersDownMeansTheFallback() {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      QuoteService quotes = new QuoteService(executor, Duration.ofMillis(100));

      assertThat(quotes.cheapest(List.of(provider("slow", 1, 1_000)), Money.of(999, "INR")))
          .isEqualTo(Money.of(999, "INR"));
    }
  }

  @Test
  void tenThousandBlockingTasksOnAHandfulOfCarrierThreads() {
    VirtualThreads.Run run = VirtualThreads.blockingTasks(10_000, Duration.ofMillis(200));

    assertThat(run.completed()).isEqualTo(10_000);
    // 10,000 × 200 ms = 2,000 s of blocking. With virtual threads it overlaps almost entirely.
    assertThat(run.elapsed()).isLessThan(Duration.ofSeconds(10));
    // …on a few OS threads (≈ number of CPU cores), not 10,000.
    assertThat(run.distinctCarriers()).isLessThan(100);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
