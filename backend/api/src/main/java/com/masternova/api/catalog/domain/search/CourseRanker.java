package com.masternova.api.catalog.domain.search;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;

/**
 * ⭐ STRATEGY: one way to rank published courses for a query. The search service picks one per
 * request by {@link #mode()} and never knows how ranking works; {@code HybridRanker} composes the
 * other two through this same interface (search LLD §4).
 */
@DesignPattern(value = Pattern.STRATEGY, role = "Strategy", note = "patterns/docs/01-strategy.md")
public interface CourseRanker {

  SearchMode mode();

  /**
   * The best {@code limit} PUBLISHED courses for the query, best first.
   *
   * @throws EmbeddingUnavailableException when the ranking needs the embedding model and it can't
   *     be reached — the caller falls back to keywords
   */
  List<RankedCourse> rank(String query, int limit);
}
