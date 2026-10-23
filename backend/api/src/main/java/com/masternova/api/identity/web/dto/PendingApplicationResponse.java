package com.masternova.api.identity.web.dto;

import com.masternova.api.identity.application.InstructorReviewService.Pending;
import java.time.Instant;

/** One row of the admin's queue: who applied, when, and why. */
public record PendingApplicationResponse(
    String userId,
    String email,
    String displayName,
    boolean emailVerified,
    String pitch,
    Instant appliedAt) {

  public static PendingApplicationResponse from(Pending p) {
    return new PendingApplicationResponse(
        p.applicant().id().toString(),
        p.applicant().email().value(),
        p.applicant().displayName(),
        p.applicant().isEmailVerified(),
        p.application().pitch(),
        p.application().appliedAt());
  }
}
