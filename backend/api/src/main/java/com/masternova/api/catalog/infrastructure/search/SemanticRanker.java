package com.masternova.api.catalog.infrastructure.search;

import com.masternova.api.catalog.domain.search.CourseRanker;
import com.masternova.api.catalog.domain.search.RankedCourse;
import com.masternova.api.catalog.domain.search.SearchMode;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Ranks by MEANING: the query's embedding against every published course's, nearest first by cosine
 * distance ({@code <=>}), through the HNSW index (V28). The visibility rule is a JOIN, not a
 * metadata filter: one query, the same rule as everywhere else (ADR-0024 §2).
 */
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "ConcreteStrategy",
    note = "patterns/docs/01-strategy.md")
class SemanticRanker implements CourseRanker {

  /** pgvector's default {@code hnsw.ef_search}: an HNSW scan returns AT MOST this many rows. */
  static final int DEFAULT_EF_SEARCH = 40;

  private final JdbcClient jdbc;
  private final TransactionOperations tx;
  private final QueryEmbedder embedder;
  private final double maxDistance;

  SemanticRanker(
      JdbcClient jdbc, TransactionOperations tx, QueryEmbedder embedder, double maxDistance) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.embedder = embedder;
    this.maxDistance = maxDistance;
  }

  @Override
  public SearchMode mode() {
    return SearchMode.SEMANTIC;
  }

  /**
   * ⭐ Three details, each measured (docs/db/indexes.md §9):
   *
   * <ul>
   *   <li>The model is called FIRST, outside any transaction: no pooled connection waits on it.
   *   <li>The ORDER BY is the index's operator on the bare column, which is what lets the planner
   *       walk the HNSW graph; the distance cut-off applies to those nearest rows afterwards (the
   *       outer query), so it can't turn the index scan into a full one.
   *   <li>{@code hnsw.ef_search} is raised to the limit for THIS transaction ({@code set_config(…,
   *       true)} = {@code SET LOCAL}): at pgvector's default of 40, asking for 60 neighbours
   *       silently returned 40, starving the hybrid fusion of candidates.
   *   <li>{@code hnsw.iterative_scan = relaxed_order} (pgvector 0.8): the graph still holds DEAD
   *       entries (deleted or re-embedded rows) until vacuum, and rows the join filters out
   *       (unpublished since the last sweep). Without it they use up the candidate list and the
   *       scan returns fewer rows, or none (found by {@code SemanticSearchIT} after deletes).
   *       "Relaxed" may return near-ties slightly out of order; the outer query re-sorts.
   * </ul>
   */
  @Override
  public List<RankedCourse> rank(String query, int limit) {
    String vector = Vectors.literal(embedder.embed(query)); // may throw: the caller degrades
    return tx.execute(s -> nearest(vector, limit));
  }

  private List<RankedCourse> nearest(String vector, int limit) {
    jdbc.sql(
            """
            SELECT set_config('hnsw.ef_search', :ef, true),
                   set_config('hnsw.iterative_scan', 'relaxed_order', true)
            """)
        .param("ef", String.valueOf(Math.max(limit, DEFAULT_EF_SEARCH)))
        .query((rs, n) -> n)
        .single();
    return jdbc.sql(
            """
            SELECT id, distance FROM (
                SELECT c.id, e.embedding <=> CAST(:vector AS vector) AS distance
                FROM course_embedding e JOIN course c ON c.id = e.course_id
                WHERE c.status = 'PUBLISHED'
                ORDER BY e.embedding <=> CAST(:vector AS vector)
                LIMIT :limit
            ) nearest
            WHERE distance <= :max
            ORDER BY distance, id
            """)
        .param("vector", vector)
        .param("limit", limit)
        .param("max", maxDistance)
        .query(
            (rs, n) ->
                new RankedCourse(rs.getObject("id", UUID.class), 1.0 - rs.getDouble("distance")))
        .list();
  }
}
