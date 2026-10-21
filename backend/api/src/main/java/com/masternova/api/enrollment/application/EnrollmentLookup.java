package com.masternova.api.enrollment.application;

import com.masternova.api.enrollment.EnrollmentApi;
import com.masternova.api.enrollment.domain.Enrollment;
import com.masternova.api.enrollment.domain.EnrollmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link EnrollmentApi}: one primary-key read per question. */
@Service
class EnrollmentLookup implements EnrollmentApi {

  private final EnrollmentRepository enrollments;

  EnrollmentLookup(EnrollmentRepository enrollments) {
    this.enrollments = enrollments;
  }

  @Override
  public boolean isEnrolled(UUID userId, UUID courseId) {
    return enrollments.find(userId, courseId).filter(Enrollment::isActive).isPresent();
  }
}
