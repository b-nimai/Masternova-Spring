package com.masternova.api.catalog.infrastructure.search;

import com.masternova.api.catalog.domain.search.CourseRanker;
import com.masternova.api.catalog.domain.search.RankedCourse;
import com.masternova.api.catalog.domain.search.SearchMode;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Ranks by WORDS: Postgres full-text search over the generated {@code course.search_vector} (V27).
 *
 * <ul>
 *   <li>⭐ {@code websearch_to_tsquery} parses what people type ({@code "exact phrase"}, {@code
 *       -not}, {@code or}) and never throws on bad syntax, unlike {@code to_tsquery}.
 *   <li>⭐ The query is parsed twice, {@code english} (stemmed) OR {@code simple}, because the
 *       vector is built with either one depending on the course's language.
 *   <li>{@code ts_rank_cd} (cover density) rewards the words appearing close together, weighted
 *       A/B/C by where they appear.
 * </ul>
 */
@Component
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "ConcreteStrategy",
    note = "patterns/docs/01-strategy.md")
class KeywordRanker implements CourseRanker {

  private final JdbcClient jdbc;

  KeywordRanker(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public SearchMode mode() {
    return SearchMode.KEYWORD;
  }

  @Override
  public List<RankedCourse> rank(String query, int limit) {
    return jdbc.sql(
            """
            SELECT c.id, ts_rank_cd(c.search_vector, q.query) AS score
            FROM course c,
                 (SELECT websearch_to_tsquery('english', :text)
                         || websearch_to_tsquery('simple', :text) AS query) q
            WHERE c.status = 'PUBLISHED' AND c.search_vector @@ q.query
            ORDER BY score DESC, c.id
            LIMIT :limit
            """)
        .param("text", query)
        .param("limit", limit)
        .query((rs, n) -> new RankedCourse(rs.getObject("id", UUID.class), rs.getDouble("score")))
        .list();
  }
}
