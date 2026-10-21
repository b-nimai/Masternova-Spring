package com.masternova.patterns.strategy.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A Spring-free copy of Masternova's search rankers (Phase 11.4, patterns/docs/01-strategy.md §7f):
 * three ways to rank, picked per request; a hybrid built from the other two; a degraded substitute
 * when one strategy's dependency is down.
 */
public final class Ranking {

  private Ranking() {}

  public enum Mode {
    KEYWORD,
    SEMANTIC,
    HYBRID
  }

  /** ⭐ The Strategy. */
  public interface Ranker {
    Mode mode();

    List<String> rank(String query, int limit);
  }

  /** The dependency (an embedding model) is unavailable: the context falls back. */
  public static final class Unavailable extends RuntimeException {
    public Unavailable() {
      super("model unavailable");
    }
  }

  /** A ConcreteStrategy over any list of documents: by shared words. */
  public static Ranker words(List<String> documents) {
    return of(Mode.KEYWORD, q -> documents.stream().filter(d -> shares(d, q)).toList());
  }

  /** A ConcreteStrategy that needs a model: here a lookup table standing in for "meaning". */
  public static Ranker meaning(Map<String, List<String>> nearest, boolean modelUp) {
    return of(
        Mode.SEMANTIC,
        q -> {
          if (!modelUp) {
            throw new Unavailable();
          }
          return nearest.getOrDefault(q, List.of());
        });
  }

  /** ⭐ A ConcreteStrategy COMPOSED of two others, through the same interface: RRF by rank. */
  public static Ranker hybrid(Ranker words, Ranker meaning) {
    return of(
        Mode.HYBRID,
        q -> {
          List<String> m = meaning.rank(q, 100); // may throw: the context degrades
          List<String> w = words.rank(q, 100);
          return fuse(List.of(w, m));
        });
  }

  /** Reciprocal Rank Fusion: score = Σ 1 / (60 + rank). */
  public static List<String> fuse(List<List<String>> lists) {
    Map<String, Double> scores = new LinkedHashMap<>();
    for (List<String> list : lists) {
      for (int i = 0; i < list.size(); i++) {
        scores.merge(list.get(i), 1.0 / (60 + i + 1), Double::sum);
      }
    }
    List<Map.Entry<String, Double>> entries = new ArrayList<>(scores.entrySet());
    entries.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));
    return entries.stream().map(Map.Entry::getKey).toList();
  }

  /** ⭐ The Context: picks by mode, never branches on HOW a ranker ranks. */
  public static final class Search {
    private final Map<Mode, Ranker> rankers = new EnumMap<>(Mode.class);

    public Search(List<Ranker> rankers) {
      rankers.forEach(r -> this.rankers.put(r.mode(), r));
    }

    public record Result(Mode ran, boolean degraded, List<String> hits) {}

    public Result search(String query, Mode mode) {
      Ranker ranker = rankers.getOrDefault(mode, rankers.get(Mode.KEYWORD));
      try {
        return new Result(ranker.mode(), ranker.mode() != mode, ranker.rank(query, 10));
      } catch (Unavailable e) { // the substitute, picked at runtime
        return new Result(Mode.KEYWORD, true, rankers.get(Mode.KEYWORD).rank(query, 10));
      }
    }
  }

  private static Ranker of(Mode mode, Function<String, List<String>> rank) {
    return new Ranker() {
      @Override
      public Mode mode() {
        return mode;
      }

      @Override
      public List<String> rank(String query, int limit) {
        return rank.apply(query).stream().limit(limit).toList();
      }
    };
  }

  private static boolean shares(String document, String query) {
    for (String word : query.toLowerCase().split(" ")) {
      if (document.toLowerCase().contains(word)) {
        return true;
      }
    }
    return false;
  }
}
