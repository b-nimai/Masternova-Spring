package com.masternova.api.entitlement.application;

import com.masternova.kernel.crypto.SignedTokens;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A learner's playback token (ADR-0017): the kernel's {@link SignedTokens} over {@code lectureId
 * "." assetId "." userId "." expiresEpochSec}, BOUND to the client IP — the IP is in the MAC, not
 * in the token.
 *
 * <p>⭐ What it proves: the entitlement decision allowed THIS user to play THIS lecture, from THIS
 * network, less than 5 minutes ago. The asset id rides along (signed), so a playlist request needs
 * no database at all.
 */
final class PlaybackTokens {

  /** What a valid token authorizes. */
  record PlaybackToken(UUID lectureId, UUID assetId, UUID userId, Instant expiresAt) {
    PlaybackToken {
      Objects.requireNonNull(lectureId, "lectureId");
      Objects.requireNonNull(assetId, "assetId");
      Objects.requireNonNull(userId, "userId");
      Objects.requireNonNull(expiresAt, "expiresAt");
    }
  }

  private final SignedTokens tokens;

  PlaybackTokens(String secret) {
    this.tokens = new SignedTokens(secret, "the entitlement playback secret");
  }

  String issue(PlaybackToken token, String clientIp) {
    return tokens.sign(
        token.lectureId()
            + "."
            + token.assetId()
            + "."
            + token.userId()
            + "."
            + token.expiresAt().getEpochSecond(),
        clientIp);
  }

  /** The token, or empty if forged, mangled, expired, for another lecture or from another IP. */
  Optional<PlaybackToken> verify(String token, UUID lectureId, String clientIp, Instant now) {
    return tokens
        .verify(token, clientIp)
        .flatMap(PlaybackTokens::parse)
        .filter(t -> t.lectureId().equals(lectureId) && now.isBefore(t.expiresAt()));
  }

  private static Optional<PlaybackToken> parse(String payload) {
    String[] parts = payload.split("\\.");
    if (parts.length != 4) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new PlaybackToken(
              UUID.fromString(parts[0]),
              UUID.fromString(parts[1]),
              UUID.fromString(parts[2]),
              Instant.ofEpochSecond(Long.parseLong(parts[3]))));
    } catch (IllegalArgumentException e) { // a bad UUID or number (NumberFormatException is one)
      return Optional.empty();
    }
  }
}
