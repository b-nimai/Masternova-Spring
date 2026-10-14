package com.masternova.api.entitlement;

import com.masternova.api.identity.CurrentUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * entitlement's PUBLIC module API (CLAUDE.md §3). Commerce (Phase 9) grants and revokes; any module
 * may ask for a decision. To GUARD a method, prefer
 * {@code @PreAuthorize("@entitlements.canWatch(authentication, #lectureId)")}, which carries the
 * denial reason into the 403.
 */
public interface EntitlementApi {

  /**
   * May this viewer (empty = anonymous) play this lecture? Throws {@code NotFoundException} for an
   * unknown lecture.
   */
  AccessDecision decide(Optional<CurrentUser> viewer, UUID lectureId);

  /**
   * Gives a user a course. ⭐ Idempotent — a webhook redelivery is a no-op — and never shortens
   * access a user already holds.
   *
   * @param expiresAt empty = lifetime access
   * @return whether anything changed
   */
  boolean grant(UUID userId, UUID courseId, EntitlementSource source, Optional<Instant> expiresAt);

  /**
   * Takes a course away (a refund, a chargeback). Idempotent; takes effect at the viewer's next
   * decision, playback tokens included within their 5 minutes (ADR-0017).
   *
   * @return whether anything changed
   */
  boolean revoke(UUID userId, UUID courseId, String reason);
}
