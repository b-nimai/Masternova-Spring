package com.masternova.api.entitlement.application;

import com.masternova.api.entitlement.AccessDecision;
import java.util.List;
import java.util.UUID;

/**
 * The course page's lock icons: one decision for the course as a whole (the call to action) and one
 * per lecture, in curriculum order.
 */
public record CourseAccess(UUID courseId, AccessDecision course, List<LectureAccess> lectures) {

  public record LectureAccess(UUID lectureId, AccessDecision decision) {}

  public CourseAccess {
    lectures = List.copyOf(lectures);
  }
}
