package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.domain.Enrollment;
import java.time.Instant;
import java.util.UUID;

/** The learner's enrollment in one course, as {@code PUT /me/enrollments/{courseId}} answers it. */
public record EnrollmentResponse(UUID courseId, String status, Instant enrolledAt) {

  public static EnrollmentResponse from(Enrollment e) {
    return new EnrollmentResponse(e.courseId(), e.status().name(), e.enrolledAt());
  }
}
