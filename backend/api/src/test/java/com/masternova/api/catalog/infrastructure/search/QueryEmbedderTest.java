package com.masternova.api.catalog.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.catalog.domain.search.EmbeddingUnavailableException;
import com.masternova.api.catalog.search.HashingEmbeddingModel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QueryEmbedderTest {

  private final HashingEmbeddingModel model = new HashingEmbeddingModel();
  private final MutableClock clock = new MutableClock();
  private final QueryEmbedder embedder =
      new QueryEmbedder(model, Duration.ofMillis(200), Duration.ofSeconds(30), clock);

  @Test
  void aFailureOpensTheBreakerForItsWindow() {
    model.failing(true);
    assertThatThrownBy(() -> embedder.embed("kubernetes"))
        .isInstanceOf(EmbeddingUnavailableException.class);
    model.failing(false);
    int calls = model.calls();

    clock.advance(Duration.ofSeconds(29));
    assertThatThrownBy(() -> embedder.embed("kubernetes")) // open: the model isn't even called
        .isInstanceOf(EmbeddingUnavailableException.class);
    assertThat(model.calls()).isEqualTo(calls);

    clock.advance(Duration.ofSeconds(2)); // the window has passed: one more try, and it works
    assertThat(embedder.embed("kubernetes")).hasSize(HashingEmbeddingModel.DIMENSIONS);
    assertThat(model.calls()).isEqualTo(calls + 1);
  }

  @Test
  void aSlowModelIsCutOffAtTheBudgetAndOpensTheBreaker() {
    model.delay(Duration.ofSeconds(5));
    long started = System.nanoTime();

    assertThatThrownBy(() -> embedder.embed("slow"))
        .isInstanceOf(EmbeddingUnavailableException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    model.delay(Duration.ZERO);
    assertThatThrownBy(() -> embedder.embed("slow")) // open
        .isInstanceOf(EmbeddingUnavailableException.class);
  }

  static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-21T10:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
