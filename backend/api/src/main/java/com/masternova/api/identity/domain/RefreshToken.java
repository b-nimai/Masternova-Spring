package com.masternova.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single-use refresh token. Only its hash is stored (ADR-0006). */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  protected RefreshToken() {}

  public static RefreshToken issue(
      UUID sessionId, String tokenHash, Instant now, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.id = UUID.randomUUID();
    token.sessionId = sessionId;
    token.tokenHash = tokenHash;
    token.issuedAt = now;
    token.expiresAt = expiresAt;
    return token;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }
}
