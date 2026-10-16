package com.masternova.api.entitlement;

import com.masternova.api.identity.CurrentUser;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
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
   * Which of these courses the user holds an ACTIVE entitlement for right now — what a cart must
   * not sell them again (Phase 9). Expired and revoked rows don't count: those may be bought.
   */
  Set<UUID> heldCourses(UUID userId, Collection<UUID> courseIds);

  /**
   * Was this user's access to the course taken away (a refund, an admin)? Free enrollment (Phase
   * 10) asks first: re-enrolling would otherwise undo the revoke, since a grant after a revoke
   * restores.
   */
  boolean isRevoked(UUID userId, UUID courseId);

  /**
   * Gives a user a course. ⭐ Idempotent against a held grant — a duplicate while the access is
   * active is a no-op — and never shortens access. A grant after a REVOKE restores access: that is
   * a re-purchase. So a caller fed by redelivering webhooks must dedupe the EVENT first (Phase 9:
   * claim the provider's event id before processing), or a late copy of the original purchase would
   * undo a refund.
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
