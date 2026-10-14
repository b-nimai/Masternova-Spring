package com.masternova.api.entitlement.domain;

import com.masternova.api.entitlement.EntitlementSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One viewer's access to one course — the row the cache stores (ADR-0016). An immutable value: a
 * grant or a revocation is a new value written by the repository, never a mutation in place.
 *
 * <p>⭐ Its status is computed AT A TIME ({@link #status(Instant)}), never stored: expiry needs no
 * job, and the cached copy can't be "still active" after it expired.
 */
public record Entitlement(
    UUID userId,
    UUID courseId,
    EntitlementSource source,
    Instant grantedAt,
    Optional<Instant> expiresAt,
    Optional<Revocation> revocation) {

  public enum Status {
    ACTIVE,
    EXPIRED,
    REVOKED
  }

  /** A refund, a chargeback, an admin's decision — with the reason kept for support. */
  public record Revocation(Instant at, String reason) {
    public Revocation {
      Objects.requireNonNull(at, "at");
      reason = Objects.requireNonNull(reason, "reason").strip();
      if (reason.isEmpty() || reason.length() > 200) {
        throw new IllegalArgumentException("a revocation reason needs 1–200 characters");
      }
    }
  }

  public Entitlement {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(grantedAt, "grantedAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(revocation, "revocation");
  }

  /** A fresh grant at {@code now}; {@code expiresAt} empty = lifetime access. */
  public static Entitlement grant(
      UUID userId,
      UUID courseId,
      EntitlementSource source,
      Instant now,
      Optional<Instant> expiresAt) {
    if (expiresAt.isPresent() && !expiresAt.get().isAfter(now)) {
      throw new IllegalArgumentException("an entitlement must expire after it is granted");
    }
    return new Entitlement(userId, courseId, source, now, expiresAt, Optional.empty());
  }

  /** ⭐ REVOKED wins over EXPIRED: a refunded rental is "revoked", which is the truer story. */
  public Status status(Instant now) {
    if (revocation.isPresent()) {
      return Status.REVOKED;
    }
    if (expiresAt.isPresent() && !now.isBefore(expiresAt.get())) {
      return Status.EXPIRED;
    }
    return Status.ACTIVE;
  }
}
