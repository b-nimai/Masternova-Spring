package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CourseRepository.TitleSuggestion;
import com.masternova.api.catalog.domain.CourseStatus;
import com.masternova.api.catalog.domain.search.CourseRanker;
import com.masternova.api.catalog.domain.search.EmbeddingUnavailableException;
import com.masternova.api.catalog.domain.search.RankedCourse;
import com.masternova.api.catalog.domain.search.SearchMode;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Course search (search LLD §4–§5). ⭐ The CONTEXT of the rankers' Strategy: it picks the ranker for
 * the requested mode and never branches on how ranking works. When a ranking needs the embedding
 * model and the model is unavailable, it substitutes the keyword ranker and says so ({@code
 * degraded}): search never fails because the model did.
 */
@Service
@DesignPattern(value = Pattern.STRATEGY, role = "Context", note = "patterns/docs/01-strategy.md")
public class CourseSearchService {

  public static final int MAX_LIMIT = 50;
  public static final int SUGGESTIONS = 8;

  private static final Logger log = LoggerFactory.getLogger(CourseSearchService.class);

  private final Map<SearchMode, CourseRanker> rankers = new EnumMap<>(SearchMode.class);
  private final CourseRepository courses;
  private final MeterRegistry meters;

  CourseSearchService(List<CourseRanker> rankers, CourseRepository courses, MeterRegistry meters) {
    rankers.forEach(r -> this.rankers.put(r.mode(), r));
    if (!this.rankers.containsKey(SearchMode.KEYWORD)) {
      throw new IllegalStateException("the keyword ranker is the fallback: it must exist");
    }
    this.courses = courses;
    this.meters = meters;
  }

  /**
   * @param ran what actually ranked (keywords when the model was unavailable)
   * @param degraded whether {@code ran} differs from the mode asked for
   * @param courses the cards, best first
   */
  public record SearchResult(SearchMode ran, boolean degraded, List<Course> courses) {}

  /**
   * ⭐ NOT one transaction: the semantic ranker waits up to 2 s on the model, and a surrounding
   * transaction would hold a pooled connection through that wait. Each ranker's query and the card
   * load are short statements of their own.
   */
  public SearchResult search(String query, SearchMode mode, int limit) {
    String text = query.strip();
    CourseRanker ranker = rankers.getOrDefault(mode, rankers.get(SearchMode.KEYWORD));
    SearchMode ran = ranker.mode();
    boolean degraded = ran != mode; // a mode with no ranker configured (semantic off)
    List<RankedCourse> hits;
    try {
      hits = ranker.rank(text, limit);
    } catch (EmbeddingUnavailableException e) {
      log.debug("search degraded to keywords: {}", e.getMessage());
      ran = SearchMode.KEYWORD;
      degraded = true;
      hits = rankers.get(SearchMode.KEYWORD).rank(text, limit);
    }
    meters
        .counter("search.requests", "mode", mode.name(), "degraded", String.valueOf(degraded))
        .increment();
    return new SearchResult(ran, degraded, inRankOrder(hits));
  }

  /** Up to 8 published titles for the typeahead: no model call, one indexed query. */
  public List<TitleSuggestion> suggest(String query) {
    return courses.suggestTitles(query.strip(), SUGGESTIONS);
  }

  /** ⭐ One statement for all the cards, then put back in the ranker's order (SQL's IN has none). */
  private List<Course> inRankOrder(List<RankedCourse> hits) {
    if (hits.isEmpty()) {
      return List.of();
    }
    Map<UUID, Course> byId =
        courses.findWithCategoryByIdIn(hits.stream().map(RankedCourse::courseId).toList()).stream()
            .collect(Collectors.toMap(Course::id, Function.identity()));
    return hits.stream()
        .map(h -> byId.get(h.courseId()))
        .filter(Objects::nonNull) // unpublished or deleted between the two statements
        .filter(c -> c.status() == CourseStatus.PUBLISHED)
        .toList();
  }
}
