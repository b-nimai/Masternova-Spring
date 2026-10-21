package com.masternova.api.catalog.infrastructure.search;

import com.masternova.api.catalog.domain.search.EmbeddingUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Embeds a search query within a TIME BUDGET, behind a tiny circuit breaker (search LLD §5).
 *
 * <ul>
 *   <li>⭐ The budget: the model runs on a virtual thread and the request waits at most {@code
 *       timeout}. A model that hangs costs one search 2 s, never a request thread forever.
 *   <li>⭐ The breaker: after a failure, every call for {@code openFor} fails at once without
 *       calling the model. Without it, a black-holed model host adds the full timeout to EVERY
 *       search. The first call after the window tries again (half-open, one probe at a time is not
 *       enforced: a few concurrent probes are harmless here).
 * </ul>
 *
 * Not Resilience4j: one breaker with two numbers, unit-tested with a clock (ADR-0024 §6).
 */
class QueryEmbedder implements AutoCloseable {

  private final EmbeddingModel model;
  private final Duration timeout;
  private final Duration openFor;
  private final Clock clock;
  private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.MIN);

  QueryEmbedder(EmbeddingModel model, Duration timeout, Duration openFor, Clock clock) {
    this.model = model;
    this.timeout = timeout;
    this.openFor = openFor;
    this.clock = clock;
  }

  /**
   * @throws EmbeddingUnavailableException when the breaker is open, or the model failed or was too
   *     slow (which opens it)
   */
  float[] embed(String text) {
    if (clock.instant().isBefore(openUntil.get())) {
      throw new EmbeddingUnavailableException("embedding breaker open", null);
    }
    CompletableFuture<float[]> call = CompletableFuture.supplyAsync(() -> model.embed(text), calls);
    try {
      float[] vector = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      openUntil.set(Instant.MIN); // a success closes it
      return vector;
    } catch (TimeoutException | ExecutionException e) {
      call.cancel(true);
      openUntil.set(clock.instant().plus(openFor));
      throw new EmbeddingUnavailableException("embedding model unavailable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EmbeddingUnavailableException("interrupted", e);
    }
  }

  @Override
  public void close() {
    calls.shutdownNow();
  }
}
