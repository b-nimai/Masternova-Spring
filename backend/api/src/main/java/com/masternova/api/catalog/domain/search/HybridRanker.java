package com.masternova.api.catalog.domain.search;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;

/**
 * Ranks by WORDS AND MEANING: asks both other rankers for candidates and fuses their lists by rank
 * ({@link RankFusion}). ⭐ A ConcreteStrategy built from two others through the same interface: it
 * knows nothing of SQL or models. If the semantic ranker can't reach its model, the exception
 * passes through and the search service answers from keywords.
 */
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "ConcreteStrategy (composes two)",
    note = "patterns/docs/01-strategy.md")
public final class HybridRanker implements CourseRanker {

  /** Candidates per list: enough overlap to fuse, bounded so a page stays cheap. */
  static final int CANDIDATES_PER_RESULT = 3;

  static final int MAX_CANDIDATES = 100;

  private final CourseRanker keyword;
  private final CourseRanker semantic;

  public HybridRanker(CourseRanker keyword, CourseRanker semantic) {
    this.keyword = keyword;
    this.semantic = semantic;
  }

  @Override
  public SearchMode mode() {
    return SearchMode.HYBRID;
  }

  @Override
  public List<RankedCourse> rank(String query, int limit) {
    int candidates = Math.min(limit * CANDIDATES_PER_RESULT, MAX_CANDIDATES);
    List<RankedCourse> byMeaning = semantic.rank(query, candidates); // first: it may throw
    List<RankedCourse> byWords = keyword.rank(query, candidates);
    return RankFusion.reciprocalRank(List.of(byWords, byMeaning), limit);
  }
}
