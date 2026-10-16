package com.masternova.api.enrollment.web;

import com.masternova.api.enrollment.application.EnrollmentService;
import com.masternova.api.enrollment.web.dto.EnrollmentResponse;
import com.masternova.api.identity.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The learner's own enrollments. Thin: parse, call one service, map. */
@RestController
@RequestMapping("/api/v1/me/enrollments")
class EnrollmentController {

  private final EnrollmentService enrollments;

  EnrollmentController(EnrollmentService enrollments) {
    this.enrollments = enrollments;
  }

  /**
   * Enroll in a free course. ⭐ PUT on the pair: idempotent by nature (enrolling twice is one
   * enrollment), so a retried request needs no Idempotency-Key. 422 {@code COURSE_NOT_FREE} for a
   * course on sale, 403 {@code ENTITLEMENT_REVOKED} after a refund or an admin's revoke.
   */
  @PutMapping("/{courseId}")
  EnrollmentResponse enroll(CurrentUser me, @PathVariable UUID courseId) {
    return EnrollmentResponse.from(enrollments.enrollFree(me.id(), courseId));
  }
}
