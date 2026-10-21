package com.masternova.api.enrollment.application;

import com.masternova.kernel.crypto.SignedTokens;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The credential of a progress beacon. {@code navigator.sendBeacon} — the one request a closing
 * page reliably sends — can't set {@code Authorization}, so the beacon carries this token in its
 * body: the kernel's {@link SignedTokens} over {@code userId "." lectureId "." expiresEpochSec},
 * BOUND to the client IP like the playback token (ADR-0017): signed, never carried.
 *
 * <p>⭐ What it proves: this user was heartbeating this lecture, from this network, moments ago. It
 * can do one thing — move that user's position in that lecture.
 */
final class BeaconTokens {

  /** What a valid token lets its bearer write. */
  record Beacon(UUID userId, UUID lectureId) {}

  private final SignedTokens tokens;

  BeaconTokens(String secret) {
    this.tokens = new SignedTokens(secret, "the progress beacon secret");
  }

  String issue(UUID userId, UUID lectureId, Instant expiresAt, String clientIp) {
    return tokens.sign(userId + "." + lectureId + "." + expiresAt.getEpochSecond(), clientIp);
  }

  /** The beacon, or empty if forged, mangled, expired or from another IP. */
  Optional<Beacon> verify(String token, String clientIp, Instant now) {
    return tokens.verify(token, clientIp).flatMap(payload -> parse(payload, now));
  }

  private static Optional<Beacon> parse(String payload, Instant now) {
    String[] parts = payload.split("\\.");
    if (parts.length != 3) {
      return Optional.empty();
    }
    try {
      Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[2]));
      return now.isBefore(expiresAt)
          ? Optional.of(new Beacon(UUID.fromString(parts[0]), UUID.fromString(parts[1])))
          : Optional.empty();
    } catch (IllegalArgumentException e) { // a bad UUID or number
      return Optional.empty();
    }
  }
}
