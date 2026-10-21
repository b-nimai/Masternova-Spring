package com.masternova.api.catalog.infrastructure.search;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
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

  /**
   * ⭐ The embedded text and its hash come from ONE expression, in SQL: the comparison and the
   * embedding can't disagree about what "the text" is. Truncated to stay inside the model's
   * context.
   */
  private static final String STALE =
      """
      WITH wanted AS (
          SELECT c.id, left(c.title || E'\\n' || coalesce(c.subtitle, '') || E'\\n'
                            || c.description, 8000) AS text
          FROM course c WHERE c.status = 'PUBLISHED'
      )
      SELECT w.id, w.text, md5(w.text) AS hash
      FROM wanted w LEFT JOIN course_embedding e ON e.course_id = w.id
      WHERE e.course_id IS NULL OR e.model <> :model OR e.content_hash <> md5(w.text)
      ORDER BY w.id
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

  /**
   * One batch: remove the vectors of courses no longer published, then embed up to {@code
   * batchSize} stale courses in ONE model call — outside any transaction (a slow call must not hold
   * a pooled connection) — and upsert them in one short transaction.
   *
   * @throws RuntimeException when the model fails: nothing is written, the next run retries
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
        jdbc.sql(STALE)
            .param("model", modelName)
            .param("batch", batchSize)
            .query(
                (rs, n) ->
                    new Stale(
                        rs.getObject("id", UUID.class), rs.getString("text"), rs.getString("hash")))
            .list();
    if (stale.isEmpty()) {
      return new Run(0, removed);
    }
    List<float[]> vectors = model.embed(stale.stream().map(Stale::text).toList());
    if (vectors.size() != stale.size()) {
      throw new IllegalStateException(
          "the model returned " + vectors.size() + " vectors for " + stale.size() + " texts");
    }
    Timestamp now = Timestamp.from(clock.instant());
    tx.executeWithoutResult(
        s -> {
          for (int i = 0; i < stale.size(); i++) {
            float[] vector = vectors.get(i);
            if (vector.length != DIMENSIONS) { // a model with another dimension needs a migration
              throw new IllegalStateException(
                  modelName
                      + " returned "
                      + vector.length
                      + " dimensions, V28 stores "
                      + DIMENSIONS);
            }
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
                .param("id", stale.get(i).courseId())
                .param("model", modelName)
                .param("hash", stale.get(i).hash())
                .param("vector", Vectors.literal(vector))
                .param("at", now)
                .update();
          }
        });
    return new Run(stale.size(), removed);
  }
}
