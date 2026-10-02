package com.masternova.api.identity;

/** What a user may do. Stored per user; carried in the JWT; checked with @PreAuthorize. */
public enum Role {
  LEARNER,
  INSTRUCTOR,
  ADMIN;

  /** Spring Security's authority name for this role, e.g. {@code ROLE_ADMIN}. */
  public String authority() {
    return "ROLE_" + name();
  }
}
