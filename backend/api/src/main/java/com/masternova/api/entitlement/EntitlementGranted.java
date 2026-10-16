package com.masternova.api.entitlement;

import com.masternova.kernel.event.DomainEvent;
import java.time.Instant;

/**
 * A user's access to a course started (or was extended, or restored after a revoke) — published in
 * the grant's transaction, only when the row actually changed. Phase 10's enrollment listens.
 *
 * @param expiresAt null = lifetime access
 * @param grantedAt when the grant happened: ⭐ a listener that may see this event late, twice or
 *     after a later revoke orders them by this time, not by arrival (ADR-0021)
 */
public record EntitlementGranted(
    String userId, String courseId, EntitlementSource source, Instant expiresAt, Instant grantedAt)
    implements DomainEvent {

  public static final String TYPE = "entitlement.granted.v1";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String aggregateId() {
    return userId + ":" + courseId;
  }
}
