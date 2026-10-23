package com.masternova.api.identity.web.dto;

import com.masternova.api.identity.domain.InstructorApplication;
import java.time.Instant;

/** The applicant's view of their own application. */
public record InstructorApplicationResponse(
    InstructorApplication.Status status, String pitch, Instant appliedAt, Instant decidedAt) {

  public static InstructorApplicationResponse from(InstructorApplication a) {
    return new InstructorApplicationResponse(a.status(), a.pitch(), a.appliedAt(), a.decidedAt());
  }
}
