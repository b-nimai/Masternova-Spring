package com.masternova.api.media.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The HMAC codec behind a playback ticket (ADR-0013, media LLD §4): {@code base64url(assetId "."
 * userId "." expiresEpochSec) "." base64url(HMAC-SHA256)} — the same shape as the kernel's {@code
 * UnsubscribeTokens}.
 *
 * <p>⭐ Why a token in the URL at all: native Safari HLS and {@code <video>} fetch playlists
 * themselves and can't add an {@code Authorization} header. So the access check runs ONCE (when the
 * ticket is issued, behind the bearer), and the ticket — one asset, one user, a few hours — is what
 * the playlist endpoints accept.
 */
final class PlaybackTickets {

  private static final String ALGORITHM = "HmacSHA256";
  private static final int MIN_SECRET_BYTES = 32;
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();

  /** What a valid ticket authorizes: this user watching this asset until then. */
  record Ticket(UUID assetId, UUID userId, Instant expiresAt) {}

  private final SecretKeySpec key;

  PlaybackTickets(String secret) {
    byte[] bytes = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "the playback secret needs at least " + MIN_SECRET_BYTES + " bytes");
    }
    this.key = new SecretKeySpec(bytes, ALGORITHM);
  }

  String issue(UUID assetId, UUID userId, Instant expiresAt) {
    String payload =
        B64.encodeToString(
            (assetId + "." + userId + "." + expiresAt.getEpochSecond())
                .getBytes(StandardCharsets.UTF_8));
    return payload + "." + B64.encodeToString(sign(payload));
  }

  /** The ticket, or empty if forged, malformed, expired — or for ANOTHER asset. */
  Optional<Ticket> verify(String token, UUID assetId, Instant now) {
    if (token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.')) {
      return Optional.empty();
    }
    String payload = token.substring(0, dot);
    try {
      // ⭐ constant-time: equals() would leak how many leading bytes of a guess were right
      if (!MessageDigest.isEqual(sign(payload), B64_DECODE.decode(token.substring(dot + 1)))) {
        return Optional.empty();
      }
      String[] parts = new String(B64_DECODE.decode(payload), StandardCharsets.UTF_8).split("\\.");
      if (parts.length != 3) {
        return Optional.empty();
      }
      Ticket ticket =
          new Ticket(
              UUID.fromString(parts[0]),
              UUID.fromString(parts[1]),
              Instant.ofEpochSecond(Long.parseLong(parts[2])));
      if (!ticket.assetId().equals(assetId) || !now.isBefore(ticket.expiresAt())) {
        return Optional.empty();
      }
      return Optional.of(ticket);
    } catch (IllegalArgumentException e) { // bad base64, bad UUID, bad number
      return Optional.empty();
    }
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM); // not thread-safe: one per call
      mac.init(key);
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is required on every JVM", e);
    }
  }
}
