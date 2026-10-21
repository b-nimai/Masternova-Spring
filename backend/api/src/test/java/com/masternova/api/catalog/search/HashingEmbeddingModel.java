package com.masternova.api.catalog.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A deterministic, network-free {@link EmbeddingModel} for tests: each word is hashed into one of
 * 768 dimensions and the vector is normalised, so texts that share words point the same way. It
 * knows no synonyms: tests prove the MECHANISM (index, freshness, ranking, degrading), not the
 * model's understanding. It can be made to fail or to be slow.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

  public static final int DIMENSIONS = 768;

  private final AtomicInteger calls = new AtomicInteger();
  private volatile boolean failing;
  private volatile Duration delay = Duration.ZERO;

  public void failing(boolean failing) {
    this.failing = failing;
  }

  public void delay(Duration delay) {
    this.delay = delay;
  }

  public int calls() {
    return calls.get();
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    calls.incrementAndGet();
    if (!delay.isZero()) {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted", e);
      }
    }
    if (failing) {
      throw new IllegalStateException("model down (test)");
    }
    List<Embedding> embeddings = new ArrayList<>();
    for (int i = 0; i < request.getInstructions().size(); i++) {
      embeddings.add(new Embedding(vector(request.getInstructions().get(i)), i));
    }
    return new EmbeddingResponse(embeddings);
  }

  @Override
  public float[] embed(Document document) {
    return embed(document.getText());
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }

  static float[] vector(String text) {
    float[] v = new float[DIMENSIONS];
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (!word.isEmpty()) {
        v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
      }
    }
    double norm = 0;
    for (float x : v) {
      norm += x * x;
    }
    if (norm == 0) {
      v[0] = 1; // an empty text still has a direction
      return v;
    }
    for (int i = 0; i < v.length; i++) {
      v[i] /= (float) Math.sqrt(norm);
    }
    return v;
  }
}
