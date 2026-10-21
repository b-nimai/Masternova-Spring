package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseSearchService;
import com.masternova.api.catalog.domain.search.SearchMode;
import com.masternova.api.catalog.web.dto.SearchResponse;
import com.masternova.api.catalog.web.dto.SuggestionResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public course search. Under {@code /search}, not {@code /courses/search}: that would shadow a
 * course whose slug is "search".
 */
@RestController
@RequestMapping("/api/v1/search")
class SearchController {

  private final CourseSearchService search;

  SearchController(CourseSearchService search) {
    this.search = search;
  }

  @GetMapping("/courses")
  SearchResponse courses(
      @RequestParam @NotBlank @Size(max = 200) String q,
      @RequestParam(defaultValue = "HYBRID") SearchMode mode,
      @RequestParam(defaultValue = "20") @Min(1) @Max(CourseSearchService.MAX_LIMIT) int limit) {
    return SearchResponse.from(search.search(q, mode, limit));
  }

  /** The typeahead: at least 2 characters (one matches half the catalog). */
  @GetMapping("/suggestions")
  List<SuggestionResponse> suggestions(@RequestParam @Size(min = 2, max = 100) String q) {
    return search.suggest(q).stream().map(SuggestionResponse::from).toList();
  }
}
