package com.masternova.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single-use, expiring token proving control of an email address. Only its hash is stored. */
@Entity
@Table(name = "verification_token")
public class VerificationToken {

  public enum Purpose {
    EMAIL_VERIFICATION
  }

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Purpose purpose;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  protected VerificationToken() {}

  public static VerificationToken issue(
      UUID userId, String tokenHash, Purpose purpose, Instant expiresAt) {
    VerificationToken token = new VerificationToken();
    token.id = UUID.randomUUID();
    token.userId = userId;
    token.tokenHash = tokenHash;
    token.purpose = purpose;
    token.expiresAt = expiresAt;
    return token;
  }

  /**
   * @return true if this call used it; false if it was expired or already used
   */
  public boolean use(Instant now) {
    if (usedAt != null || !expiresAt.isAfter(now)) {
      return false;
    }
    usedAt = now;
    return true;
  }

  public UUID userId() {
    return userId;
  }
}
