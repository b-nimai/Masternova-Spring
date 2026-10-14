package com.masternova.api.entitlement.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Where entitlement rows live. ⭐ An interface because there are TWO implementations, stacked: the
 * JDBC one (the truth) and a Redis Decorator in front of it (ADR-0016). Every writer of the row is
 * a method here, so the cache sees every write and can't miss an invalidation.
 */
public interface EntitlementRepository {

  /** The row for this pair, whatever its status, or empty. */
  Optional<Entitlement> find(UUID userId, UUID courseId);

  /**
   * Upserts a grant. ⭐ Idempotent and never shortening: a row that is already active for longer
   * (lifetime, or a later expiry) is left alone; a revoked or shorter one is replaced.
   *
   * @return whether the row changed — {@code false} for a duplicate (a webhook redelivery)
   */
  boolean grant(Entitlement grant);

  /**
   * Revokes the pair's row if it isn't already.
   *
   * @return whether the row changed — {@code false} if there is none or it was already revoked
   */
  boolean revoke(UUID userId, UUID courseId, Entitlement.Revocation revocation);
}
