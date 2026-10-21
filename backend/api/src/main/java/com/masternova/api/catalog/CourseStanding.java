package com.masternova.api.catalog;

import com.masternova.api.catalog.CourseFacts.Availability;
import java.util.Objects;
import java.util.UUID;

/**
 * Who teaches a course and whether it's on sale — the two facts an access check needs, read in ONE
 * small query (no curriculum). Added in Phase 11 for engagement, whose checks run on every
 * course-page view; {@link CourseFacts} also loads every lecture.
 */
public record CourseStanding(UUID courseId, UUID instructorId, Availability availability) {
  public CourseStanding {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(instructorId, "instructorId");
    Objects.requireNonNull(availability, "availability");
  }
}
