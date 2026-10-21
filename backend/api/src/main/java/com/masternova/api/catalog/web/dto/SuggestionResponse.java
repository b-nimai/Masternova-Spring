package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.CourseRepository.TitleSuggestion;

/** One typeahead row: the title to show and the course page it opens. */
public record SuggestionResponse(String slug, String title) {

  public static SuggestionResponse from(TitleSuggestion s) {
    return new SuggestionResponse(s.getSlug(), s.getTitle());
  }
}
