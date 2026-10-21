package com.masternova.api.catalog.infrastructure.search;

import com.masternova.api.catalog.domain.search.CourseRanker;
import com.masternova.api.catalog.domain.search.RankedCourse;
import com.masternova.api.catalog.domain.search.SearchMode;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

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

  private final JdbcClient jdbc;
  private final QueryEmbedder embedder;
  private final double maxDistance;

  SemanticRanker(JdbcClient jdbc, QueryEmbedder embedder, double maxDistance) {
    this.jdbc = jdbc;
    this.embedder = embedder;
    this.maxDistance = maxDistance;
  }

  @Override
  public SearchMode mode() {
    return SearchMode.SEMANTIC;
  }

  /**
   * ⭐ Measured details (docs/db/indexes.md §9):
   *
   * <ul>
   *   <li>The model is called FIRST, with no transaction open: no pooled connection waits on it.
   *   <li>The ORDER BY is the index's operator on the bare column, which is what lets the planner
   *       walk the HNSW graph; the distance cut-off applies to those nearest rows afterwards (the
   *       outer query), so it can't turn the index scan into a full one.
   *   <li>The scan's {@code hnsw.ef_search} (100) and {@code hnsw.iterative_scan} are set on every
   *       connection ({@code connection-init-sql}): at the default of 40, asking for 60 neighbours
   *       silently returned 40, and dead graph entries hid live rows.
   * </ul>
   */
  @Override
  public List<RankedCourse> rank(String query, int limit) {
    String vector = Vectors.literal(embedder.embed(query)); // may throw: the caller degrades
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
