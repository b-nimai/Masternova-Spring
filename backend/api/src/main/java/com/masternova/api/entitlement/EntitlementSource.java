package com.masternova.api.entitlement;

/** Where an entitlement came from. Stored as text (a CHECK constraint lists these). */
public enum EntitlementSource {
  /** A paid order (Phase 9). */
  PURCHASE,
  /** Enrolling in a free course (Phase 10). */
  FREE_ENROLLMENT,
  /** An admin gave access by hand (support, a comp). */
  ADMIN_GRANT
}
