package com.masternova.kernel.notification;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The HMAC codec behind every unsubscribe link (docs/lld/notification.md §5). The WORKER issues
 * tokens into emails; the API verifies them — both use this one class, so they can't disagree on
 * the format.
 *
 * <pre>
 *   base64url(userId "." category "." expiresEpochSec) "." base64url(HMAC-SHA256(secret, part1))
 * </pre>
 *
 * <p>⭐ Stateless: nothing is stored per link. The signature proves the link came from us and names
 * exactly one user + one category; the expiry bounds how long a leaked link works.
 */
public final class UnsubscribeTokens {

  /** How long an unsubscribe link in an email keeps working. */
  public static final Duration TTL = Duration.ofDays(30);

  private static final String ALGORITHM = "HmacSHA256";
  private static final int MIN_SECRET_BYTES =
      32; // HMAC-SHA256: a key shorter than the hash is weak
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();

  /** What a valid token authorizes: opting this user out of this category. */
  public record Unsubscribe(UUID userId, NotificationCategory category) {}

  private final SecretKeySpec key;

  public UnsubscribeTokens(String secret) {
    byte[] bytes = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "the unsubscribe token secret needs at least " + MIN_SECRET_BYTES + " bytes");
    }
    this.key = new SecretKeySpec(bytes, ALGORITHM);
  }

  public String issue(UUID userId, NotificationCategory category, Instant expiresAt) {
    String payload =
        B64.encodeToString(
            (userId + "." + category.name() + "." + expiresAt.getEpochSecond())
                .getBytes(StandardCharsets.UTF_8));
    return payload + "." + B64.encodeToString(sign(payload));
  }

  /**
   * @return the unsubscribe this token authorizes, or empty if it is forged, malformed or expired —
   *     callers get no hint which, so a forger learns nothing
   */
  public Optional<Unsubscribe> verify(String token, Instant now) {
    if (token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.')) {
      return Optional.empty();
    }
    String payload = token.substring(0, dot);
    try {
      byte[] signature = B64_DECODE.decode(token.substring(dot + 1));
      // ⭐ constant-time comparison: equals() returns at the first differing byte, and that
      //    timing difference lets an attacker guess a valid signature byte by byte
      if (!MessageDigest.isEqual(sign(payload), signature)) {
        return Optional.empty();
      }
      // only parse what we signed ourselves
      String[] parts = new String(B64_DECODE.decode(payload), StandardCharsets.UTF_8).split("\\.");
      if (parts.length != 3) {
        return Optional.empty();
      }
      Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[2]));
      if (!now.isBefore(expiresAt)) {
        return Optional.empty();
      }
      return Optional.of(
          new Unsubscribe(UUID.fromString(parts[0]), NotificationCategory.valueOf(parts[1])));
    } catch (IllegalArgumentException e) { // bad base64, bad UUID, unknown category, bad number
      return Optional.empty();
    }
  }

  private byte[] sign(String payload) {
    try {
      // Mac is NOT thread-safe: a fresh instance per call (cheap) instead of a shared field
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is required on every JVM", e);
    }
  }
}
