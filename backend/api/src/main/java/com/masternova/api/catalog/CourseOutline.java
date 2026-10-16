package com.masternova.api.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A course as a learner's dashboard shows it (enrollment, Phase 10): its name, who teaches it, and
 * its lectures in curriculum order. Any state: a learner keeps a course its instructor unpublished.
 */
public record CourseOutline(
    UUID courseId,
    String slug,
    String title,
    String instructorName,
    List<LectureOutline> lectures) {

  public CourseOutline {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(slug, "slug");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(instructorName, "instructorName");
    lectures = List.copyOf(lectures);
  }
}
