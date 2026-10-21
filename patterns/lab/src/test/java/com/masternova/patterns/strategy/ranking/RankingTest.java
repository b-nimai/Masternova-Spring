package com.masternova.patterns.strategy.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.patterns.strategy.ranking.Ranking.Mode;
import com.masternova.patterns.strategy.ranking.Ranking.Search;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RankingTest {

  private final List<String> docs =
      List.of("Kubernetes in practice", "Container orchestration", "Watercolour");
  private final Map<String, List<String>> nearest =
      Map.of("kubernetes", List.of("Container orchestration", "Kubernetes in practice"));

  private Search search(boolean modelUp) {
    var words = Ranking.words(docs);
    var meaning = Ranking.meaning(nearest, modelUp);
    return new Search(List.of(words, meaning, Ranking.hybrid(words, meaning)));
  }

  @Test
  void eachModeIsItsOwnRanking() {
    assertThat(search(true).search("kubernetes", Mode.KEYWORD).hits())
        .containsExactly("Kubernetes in practice");
    assertThat(search(true).search("kubernetes", Mode.SEMANTIC).hits())
        .containsExactly("Container orchestration", "Kubernetes in practice");
  }

  @Test
  void hybridPutsWhatBothFoundFirst() {
    assertThat(search(true).search("kubernetes", Mode.HYBRID).hits())
        .first()
        .isEqualTo("Kubernetes in practice"); // rank 1 + rank 2 beats rank 1 alone
  }

  @Test
  void aDownModelDegradesToKeywordsInsteadOfFailing() {
    Search.Result result = search(false).search("kubernetes", Mode.HYBRID);
    assertThat(result.ran()).isEqualTo(Mode.KEYWORD);
    assertThat(result.degraded()).isTrue();
    assertThat(result.hits()).containsExactly("Kubernetes in practice");
  }

  @Test
  void aModeWithNoRankerIsAlsoDegraded() {
    Search keywordsOnly = new Search(List.of(Ranking.words(docs)));
    assertThat(keywordsOnly.search("kubernetes", Mode.SEMANTIC).degraded()).isTrue();
  }
}
