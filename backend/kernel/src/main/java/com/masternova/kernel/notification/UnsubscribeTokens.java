package com.masternova.kernel.notification;

import com.masternova.kernel.crypto.SignedTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The HMAC codec behind every unsubscribe link (docs/lld/notification.md §5). The WORKER issues
 * tokens into emails; the API verifies them — both use this one class, so they can't disagree on
 * the format.
 *
 * <pre>
 *   SignedTokens: base64url(userId "." category "." expiresEpochSec) "." base64url(HMAC-SHA256)
 * </pre>
 *
 * <p>⭐ Stateless: nothing is stored per link. The signature proves the link came from us and names
 * exactly one user + one category; the expiry bounds how long a leaked link works.
 */
public final class UnsubscribeTokens {

  /** How long an unsubscribe link in an email keeps working. */
  public static final Duration TTL = Duration.ofDays(30);

  /** What a valid token authorizes: opting this user out of this category. */
  public record Unsubscribe(UUID userId, NotificationCategory category) {}

  private final SignedTokens tokens;

  public UnsubscribeTokens(String secret) {
    this.tokens = new SignedTokens(secret, "the unsubscribe token secret");
  }

  public String issue(UUID userId, NotificationCategory category, Instant expiresAt) {
    return tokens.sign(userId + "." + category.name() + "." + expiresAt.getEpochSecond());
  }

  /**
   * @return the unsubscribe this token authorizes, or empty if it is forged, malformed or expired —
   *     callers get no hint which, so a forger learns nothing
   */
  public Optional<Unsubscribe> verify(String token, Instant now) {
    return tokens.verify(token).flatMap(payload -> parse(payload, now)); // only what we signed
  }

  private static Optional<Unsubscribe> parse(String payload, Instant now) {
    String[] parts = payload.split("\\.");
    if (parts.length != 3) {
      return Optional.empty();
    }
    try {
      if (!now.isBefore(Instant.ofEpochSecond(Long.parseLong(parts[2])))) {
        return Optional.empty();
      }
      return Optional.of(
          new Unsubscribe(UUID.fromString(parts[0]), NotificationCategory.valueOf(parts[1])));
    } catch (IllegalArgumentException e) { // bad UUID, unknown category, bad number
      return Optional.empty();
    }
  }
}
