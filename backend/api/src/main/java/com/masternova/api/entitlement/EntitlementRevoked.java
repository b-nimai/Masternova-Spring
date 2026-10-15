package com.masternova.api.entitlement;

import com.masternova.kernel.event.DomainEvent;
import java.time.Instant;

/**
 * A user's access to a course was taken away (a refund, an admin) — published in the revoke's
 * transaction, only when the row actually changed. The OBSERVER seam for whatever must follow a
 * revoke without entitlement knowing it: Phase 10 drops the enrollment.
 */
public record EntitlementRevoked(String userId, String courseId, String reason, Instant revokedAt)
    implements DomainEvent {

  public static final String TYPE = "entitlement.revoked.v1";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String aggregateId() {
    return userId + ":" + courseId;
  }
}
