package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.application.CourseAuthoringService.NewCourse;
import com.masternova.api.catalog.domain.CourseLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The wizard's first step. */
public record CreateCourseRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 80) String categorySlug,
    @NotNull CourseLevel level,
    @NotNull @Pattern(regexp = "[a-z]{2}") String language) {

  public NewCourse toNewCourse() {
    return new NewCourse(title.strip(), categorySlug, level, language);
  }
}
