package com.masternova.api.catalog.domain.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankFusionTest {

  private final UUID a = UUID.randomUUID();
  private final UUID b = UUID.randomUUID();
  private final UUID c = UUID.randomUUID();
  private final UUID d = UUID.randomUUID();

  @Test
  void aCourseFoundByBothListsBeatsEachListsFavourite() {
    List<RankedCourse> words = list(a, c, d);
    List<RankedCourse> meaning = list(b, c);

    List<UUID> fused = ids(RankFusion.reciprocalRank(List.of(words, meaning), 10));

    // c: 1/62 + 1/62 = 0.0323 > a: 1/61 = 0.0164 = b
    assertThat(fused).containsExactly(c, a, b, d);
  }

  @Test
  void scoresDontMatterOnlyRanks() {
    List<RankedCourse> tiny = List.of(new RankedCourse(a, 0.0001), new RankedCourse(b, 0.00001));
    List<RankedCourse> huge = List.of(new RankedCourse(b, 9_000), new RankedCourse(a, 1));

    // symmetric ranks → equal scores → the order of first appearance
    assertThat(ids(RankFusion.reciprocalRank(List.of(tiny, huge), 10))).containsExactly(a, b);
  }

  @Test
  void theLimitAndEmptyListsAreRespected() {
    assertThat(RankFusion.reciprocalRank(List.of(List.of(), List.of()), 10)).isEmpty();
    assertThat(ids(RankFusion.reciprocalRank(List.of(list(a, b, c), List.of()), 2)))
        .containsExactly(a, b);
  }

  @Test
  void theScoreIsTheSumOfReciprocalRanks() {
    RankedCourse top = RankFusion.reciprocalRank(List.of(list(a), list(b, a)), 1).getFirst();
    assertThat(top.courseId()).isEqualTo(a);
    assertThat(top.score()).isEqualTo(1.0 / 61 + 1.0 / 62);
  }

  static List<RankedCourse> list(UUID... ids) {
    return Arrays.stream(ids).map(id -> new RankedCourse(id, 1)).toList();
  }

  static List<UUID> ids(List<RankedCourse> ranked) {
    return ranked.stream().map(RankedCourse::courseId).toList();
  }
}
