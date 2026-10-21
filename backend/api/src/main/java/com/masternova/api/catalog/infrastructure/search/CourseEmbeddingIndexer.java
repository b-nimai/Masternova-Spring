package com.masternova.api.catalog.infrastructure.search;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Keeps {@code course_embedding} in step with the published catalog, a batch per run (search LLD
 * §5). ⭐ A SWEEPER, not an event listener: it compares what each embedding was made from (model,
 * md5 of the text) with what the course would produce now, so a missed event, a model outage, an
 * edit and a model change all heal the same way, on the next run.
 */
class CourseEmbeddingIndexer {

  static final int DIMENSIONS = 768; // V28's vector(768): nomic-embed-text

  private static final Logger log = LoggerFactory.getLogger(CourseEmbeddingIndexer.class);

  /**
   * ⭐ Staleness by HASH: {@code course.embedding_text_hash} is a generated column (V29), so a sweep
   * with nothing to do compares two short strings per course instead of rebuilding and hashing
   * every description. Only the stale batch's text is built, by the same SQL function the hash is
   * made from (one definition).
   */
  private static final String STALE =
      """
      SELECT c.id, catalog_embedding_text(c.title, c.subtitle, c.description) AS text,
             c.embedding_text_hash AS hash
      FROM course c LEFT JOIN course_embedding e ON e.course_id = c.id
      WHERE c.status = 'PUBLISHED'
        AND (e.course_id IS NULL OR e.model <> :model OR e.content_hash <> c.embedding_text_hash)
      ORDER BY c.id
      LIMIT :batch
      """;

  private record Stale(UUID courseId, String text, String hash) {}

  /** What one run did. */
  record Run(int embedded, int removed) {}

  private final JdbcClient jdbc;
  private final EmbeddingModel model;
  private final TransactionOperations tx;
  private final String modelName;
  private final int batchSize;
  private final Clock clock;

  CourseEmbeddingIndexer(
      JdbcClient jdbc,
      EmbeddingModel model,
      TransactionOperations tx,
      String modelName,
      int batchSize,
      Clock clock) {
    this.jdbc = jdbc;
    this.model = model;
    this.tx = tx;
    this.modelName = modelName;
    this.batchSize = batchSize;
    this.clock = clock;
  }

  int batchSize() {
    return batchSize;
  }

  /**
   * Texts the model refused, by course → the hash of the refused text. ⭐ Remembered so a refused
   * text costs ONE try, not a failed batch plus 32 single calls every run; an edit changes the hash
   * and the course is tried again. Per replica and in memory: a restart retries each once.
   */
  private final Map<UUID, String> refused = new ConcurrentHashMap<>();

  /**
   * One batch: remove the vectors of courses no longer published, then embed up to {@code
   * batchSize} stale courses in ONE model call — outside any transaction (a slow call must not hold
   * a pooled connection) — and upsert them in one short transaction.
   *
   * <p>⭐ If the batch call fails, each text is tried ALONE, so one text the model refuses can't
   * block the courses after it; a text refused alone is remembered ({@link #refused}) and skipped
   * until it changes.
   *
   * @throws RuntimeException when the model is down (the batch and the first single tries all
   *     fail): nothing is written, the next run retries
   */
  Run indexBatch() {
    int removed =
        jdbc.sql(
                """
                DELETE FROM course_embedding e USING course c
                WHERE c.id = e.course_id AND c.status <> 'PUBLISHED'
                """)
            .update();
    List<Stale> stale =
        jdbc
            .sql(STALE)
            .param("model", modelName)
            .param("batch", batchSize + refused.size()) // room for the ones skipped below
            .query(
                (rs, n) ->
                    new Stale(
                        rs.getObject("id", UUID.class), rs.getString("text"), rs.getString("hash")))
            .list()
            .stream()
            .filter(c -> !c.hash().equals(refused.get(c.courseId())))
            .limit(batchSize)
            .toList();
    if (stale.isEmpty()) {
      return new Run(0, removed);
    }
    List<Embedded> embedded = embed(stale);
    Timestamp now = Timestamp.from(clock.instant());
    tx.executeWithoutResult(
        s -> {
          for (Embedded e : embedded) {
            // ⭐ the hash of the text EMBEDDED: if the course changed meanwhile, the next run sees
            //    a mismatch and redoes it
            jdbc.sql(
                    """
                    INSERT INTO course_embedding (course_id, model, content_hash, embedding,
                                                  embedded_at)
                    SELECT :id, :model, :hash, CAST(:vector AS vector), :at
                    WHERE EXISTS (SELECT 1 FROM course WHERE id = :id)
                    ON CONFLICT (course_id) DO UPDATE
                       SET model = EXCLUDED.model, content_hash = EXCLUDED.content_hash,
                           embedding = EXCLUDED.embedding, embedded_at = EXCLUDED.embedded_at
                    """)
                .param("id", e.course().courseId())
                .param("model", modelName)
                .param("hash", e.course().hash())
                .param("vector", Vectors.literal(e.vector()))
                .param("at", now)
                .update();
          }
        });
    return new Run(embedded.size(), removed);
  }

  private record Embedded(Stale course, float[] vector) {
    /** The model's output checked BEFORE anything is written: V28 stores {@code vector(768)}. */
    Embedded {
      if (vector.length != DIMENSIONS) { // a model with another dimension needs a migration
        throw new IllegalStateException(
            "the model returned " + vector.length + " dimensions, V28 stores " + DIMENSIONS);
      }
    }
  }

  private List<Embedded> embed(List<Stale> stale) {
    List<float[]> vectors;
    try {
      vectors = model.embed(stale.stream().map(Stale::text).toList());
    } catch (RuntimeException batchFailed) {
      return embedAlone(stale, batchFailed);
    }
    if (vectors.size() != stale.size()) {
      throw new IllegalStateException(
          "the model returned " + vectors.size() + " vectors for " + stale.size() + " texts");
    }
    List<Embedded> all = new ArrayList<>();
    for (int i = 0; i < stale.size(); i++) {
      all.add(new Embedded(stale.get(i), vectors.get(i)));
    }
    return all;
  }

  /**
   * Each text alone, after a failed batch. ⭐ Two failures before any success is an OUTAGE, not bad
   * texts: stop (a hanging model would otherwise cost 32 read timeouts) and remember nothing.
   */
  private List<Embedded> embedAlone(List<Stale> stale, RuntimeException batchFailed) {
    List<Embedded> alone = new ArrayList<>();
    List<Stale> failed = new ArrayList<>();
    for (Stale course : stale) {
      try {
        alone.add(new Embedded(course, model.embed(course.text())));
      } catch (RuntimeException e) {
        failed.add(course);
        if (alone.isEmpty() && failed.size() >= 2) {
          throw batchFailed;
        }
      }
    }
    if (alone.isEmpty()) {
      throw batchFailed; // the only text failed: indistinguishable from an outage
    }
    for (Stale course : failed) { // the model answers others: these texts are refused
      refused.put(course.courseId(), course.hash());
      log.warn(
          "course {}: the model refused its text, skipped until it changes", course.courseId());
    }
    return alone;
  }
}
