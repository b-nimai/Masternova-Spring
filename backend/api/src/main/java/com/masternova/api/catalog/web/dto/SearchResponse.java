package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.application.CourseSearchService.SearchResult;
import com.masternova.api.catalog.domain.search.SearchMode;
import java.util.List;

/**
 * Ranked search results, best first: no cursor (relevance is computed per query; search LLD §7).
 *
 * @param mode the ranking that actually ran
 * @param degraded semantic ranking was asked for and the model was unavailable: these are keyword
 *     results
 */
public record SearchResponse(SearchMode mode, boolean degraded, List<CourseSummary> items) {

  public static SearchResponse from(SearchResult r) {
    return new SearchResponse(
        r.ran(), r.degraded(), r.courses().stream().map(CourseSummary::from).toList());
  }
}
