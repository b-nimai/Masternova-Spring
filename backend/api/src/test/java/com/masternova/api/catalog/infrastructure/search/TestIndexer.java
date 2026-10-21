package com.masternova.api.catalog.infrastructure.search;

import java.util.List;
import org.springframework.boot.test.context.TestComponent;

/** Lets a test in another package run the (package-private) embedding sweep by hand. */
@TestComponent
public class TestIndexer {

  private final CourseEmbeddingIndexer indexer;

  TestIndexer(CourseEmbeddingIndexer indexer) {
    this.indexer = indexer;
  }

  /** {@code [embedded, removed]} */
  public List<Integer> run() {
    CourseEmbeddingIndexer.Run run = indexer.indexBatch();
    return List.of(run.embedded(), run.removed());
  }
}
