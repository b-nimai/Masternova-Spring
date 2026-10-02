package com.masternova.java.concurrency;

import com.masternova.java.valueobject.Money;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fan-out / fan-in with {@link CompletableFuture}: ask several tax/FX providers for a quote AT THE
 * SAME TIME, then combine. Total time ≈ the slowest call, not the sum. A slow or failing provider
 * gets a timeout and a fallback instead of failing the checkout.
 */
public final class QuoteService {

  /** One remote provider (a slow, blocking HTTP call in real life). */
  public record Provider(String name, Supplier<Money> call) {}

  private final ExecutorService executor;
  private final Duration timeout;

  public QuoteService(ExecutorService executor, Duration timeout) {
    this.executor = Objects.requireNonNull(executor, "executor");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  /** The cheapest quote among providers that answered in time; failures/timeouts are skipped. */
  public Money cheapest(List<Provider> providers, Money fallback) {
    List<CompletableFuture<Money>> calls =
        providers.stream()
            .map(
                p ->
                    // ⭐ supplyAsync(task, executor): run on OUR executor (virtual threads), not the
                    //    shared ForkJoin common pool — blocking calls must never run there.
                    CompletableFuture.supplyAsync(p.call(), executor)
                        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS) // ⭐ never wait forever
                        .exceptionally(error -> null)) // a failed/late provider → no quote
            .toList();

    // ⭐ allOf: a future that completes when every call is done (successfully or not)
    CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();

    return calls.stream()
        .map(CompletableFuture::join) // all complete already: join() doesn't block
        .filter(Objects::nonNull)
        .min(Money::compareTo)
        .orElse(fallback);
  }
}
