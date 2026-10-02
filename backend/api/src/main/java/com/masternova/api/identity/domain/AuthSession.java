package com.masternova.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One login on one device — the FAMILY of refresh tokens that rotate within it. References the user
 * by id (a separate aggregate), not by a JPA relationship.
 */
@Entity
@Table(name = "auth_session")
public class AuthSession {

  public enum RevokeReason {
    LOGOUT,
    REUSE_DETECTED,
    ADMIN
  }

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "last_used_at", nullable = false)
  private Instant lastUsedAt;

  @Column(name = "user_agent", length = 300)
  private String userAgent;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "revoke_reason", length = 30)
  private RevokeReason revokeReason;

  @Version private Long version;

  protected AuthSession() {}

  public static AuthSession start(UUID userId, String userAgent, Instant now) {
    AuthSession session = new AuthSession();
    session.id = UUID.randomUUID();
    session.userId = userId;
    session.createdAt = now;
    session.lastUsedAt = now;
    session.userAgent =
        userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
    return session;
  }

  public void touch(Instant now) {
    lastUsedAt = now;
  }

  /** Once revoked, a session stays revoked — the first reason wins. */
  public void revoke(RevokeReason reason, Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
      revokeReason = reason;
    }
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public RevokeReason revokeReason() {
    return revokeReason;
  }
}
