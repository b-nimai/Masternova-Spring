package com.masternova.api.engagement.domain;

import java.util.Objects;
import java.util.UUID;

/** The course a caller was let in to, and who teaches it (their posts are flagged). */
public record CourseRef(UUID courseId, UUID instructorId) {
  public CourseRef {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(instructorId, "instructorId");
  }
}
