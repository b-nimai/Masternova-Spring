package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.application.CourseAuthoringService.Details;
import com.masternova.api.catalog.domain.CourseLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The details step, autosaved. ⭐ {@code expectedVersion} is the version the editor last received: a
 * stale tab gets 409 instead of overwriting (API conventions §3).
 */
public record UpdateDetailsRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotBlank @Size(max = 120) String title,
    @Size(max = 200) String subtitle,
    @NotNull @Size(max = 5000) String description,
    @NotBlank @Size(max = 80) String categorySlug,
    @NotNull CourseLevel level,
    @NotNull @Pattern(regexp = "[a-z]{2}") String language) {

  public Details toDetails() {
    return new Details(title, subtitle, description, categorySlug, level, language);
  }
}
