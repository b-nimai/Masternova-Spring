package com.masternova.api.catalog.domain.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ⭐ RECIPROCAL RANK FUSION (Cormack et al., 2009): combines ranked lists whose SCORES aren't
 * comparable (a {@code ts_rank_cd} and a cosine distance) by their RANKS only:
 *
 * <pre>score(d) = Σ over lists  1 / (k + rank(d))      rank = 1, 2, 3 …</pre>
 *
 * A course found by both lists beats one found by either; {@code k = 60} (the paper's) damps the
 * gap between rank 1 and rank 2, so one list's favourite can't win alone. No weights to tune.
 */
public final class RankFusion {

  public static final int K = 60;

  private RankFusion() {}

  /** The fused list, best first; ties keep the order of first appearance. */
  public static List<RankedCourse> reciprocalRank(List<List<RankedCourse>> lists, int limit) {
    Map<UUID, Double> scores = new LinkedHashMap<>(); // ⭐ insertion order = tie-break
    for (List<RankedCourse> list : lists) {
      for (int i = 0; i < list.size(); i++) {
        scores.merge(list.get(i).courseId(), 1.0 / (K + i + 1), Double::sum);
      }
    }
    return scores.entrySet().stream()
        .map(e -> new RankedCourse(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingDouble(RankedCourse::score).reversed()) // stable sort
        .limit(limit)
        .toList();
  }
}
