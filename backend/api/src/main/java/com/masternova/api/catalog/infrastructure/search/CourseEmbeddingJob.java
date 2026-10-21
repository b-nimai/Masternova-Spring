package com.masternova.api.catalog.infrastructure.search;

import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the embedding sweep every minute on ONE replica (ShedLock). A model outage is logged once
 * when it starts and once when it ends, not every minute.
 */
class CourseEmbeddingJob {

  private static final Logger log = LoggerFactory.getLogger(CourseEmbeddingJob.class);

  private final CourseEmbeddingIndexer indexer;
  static final int MAX_BATCHES_PER_RUN = 10;

  private final AtomicBoolean failing = new AtomicBoolean();

  CourseEmbeddingJob(CourseEmbeddingIndexer indexer) {
    this.indexer = indexer;
  }

  @Scheduled(
      fixedDelayString = "${masternova.search.embedding.index-every:60s}",
      initialDelayString = "${masternova.search.embedding.index-after:30s}")
  @SchedulerLock(name = "course-embedding-index", lockAtMostFor = "5m")
  void index() {
    try {
      // ⭐ a full batch means more are waiting: keep going (a backlog — a new model, a big import —
      //    drains at 320 a minute, not 32), bounded well inside the 5 min lock
      CourseEmbeddingIndexer.Run run;
      int batches = 0;
      do {
        run = indexer.indexBatch();
        if (run.embedded() > 0 || run.removed() > 0) {
          log.info("course embeddings: {} embedded, {} removed", run.embedded(), run.removed());
        }
      } while (run.embedded() == indexer.batchSize() && ++batches < MAX_BATCHES_PER_RUN);
      if (failing.getAndSet(false)) {
        log.info("course embeddings: the model answers again");
      }
    } catch (RuntimeException e) { // the next run retries; never kill the scheduler thread
      if (!failing.getAndSet(true)) {
        log.warn("course embeddings: the sweep failed, retrying every run", e);
      }
    }
  }
}
