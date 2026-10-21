package com.masternova.api.catalog.domain.search;

import static com.masternova.api.catalog.domain.search.RankFusionTest.ids;
import static com.masternova.api.catalog.domain.search.RankFusionTest.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HybridRankerTest {

  private final UUID a = UUID.randomUUID();
  private final UUID b = UUID.randomUUID();

  @Test
  void itFusesBothRankersAndAsksEachForMoreCandidatesThanResults() {
    List<Integer> asked = new ArrayList<>();
    CourseRanker words = ranker(SearchMode.KEYWORD, asked, list(a));
    CourseRanker meaning = ranker(SearchMode.SEMANTIC, asked, list(b, a));

    List<RankedCourse> fused = new HybridRanker(words, meaning).rank("q", 5);

    assertThat(ids(fused)).containsExactly(a, b);
    assertThat(asked).containsExactly(15, 15); // 3 × limit
    new HybridRanker(words, meaning).rank("q", 50);
    assertThat(asked.getLast()).isEqualTo(100); // capped
  }

  @Test
  void aLimitOutsideItsBoundsIsRefusedBeforeAnyArithmetic() {
    CourseRanker words = ranker(SearchMode.KEYWORD, new ArrayList<>(), list(a));
    HybridRanker hybrid = new HybridRanker(words, words);

    assertThatThrownBy(() -> hybrid.rank("q", 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> hybrid.rank("q", Integer.MAX_VALUE)) // × 3 would overflow
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aModelFailurePassesThroughForTheServiceToDegrade() {
    CourseRanker words = ranker(SearchMode.KEYWORD, new ArrayList<>(), list(a));
    CourseRanker broken =
        new CourseRanker() {
          @Override
          public SearchMode mode() {
            return SearchMode.SEMANTIC;
          }

          @Override
          public List<RankedCourse> rank(String query, int limit) {
            throw new EmbeddingUnavailableException("down", null);
          }
        };

    assertThatThrownBy(() -> new HybridRanker(words, broken).rank("q", 5))
        .isInstanceOf(EmbeddingUnavailableException.class);
  }

  private static CourseRanker ranker(
      SearchMode mode, List<Integer> asked, List<RankedCourse> answer) {
    return new CourseRanker() {
      @Override
      public SearchMode mode() {
        return mode;
      }

      @Override
      public List<RankedCourse> rank(String query, int limit) {
        asked.add(limit);
        return answer;
      }
    };
  }
}
