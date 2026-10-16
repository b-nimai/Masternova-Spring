package com.masternova.api.enrollment.domain;

/** Stored as text (a CHECK constraint lists these). */
public enum EnrollmentStatus {
  /** On the learner's dashboard. */
  ACTIVE,
  /** Access was taken away (a refund, an admin); progress is kept for a re-purchase. */
  REVOKED
}
