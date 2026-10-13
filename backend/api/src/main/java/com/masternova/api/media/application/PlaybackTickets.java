package com.masternova.api.media.application;

import com.masternova.kernel.crypto.SignedTokens;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A playback ticket (ADR-0013, media LLD §4): the kernel's {@link SignedTokens} over {@code assetId
 * "." userId "." expiresEpochSec} — the same codec as the unsubscribe links.
 *
 * <p>⭐ Why a token in the URL at all: native Safari HLS and {@code <video>} fetch playlists
 * themselves and can't add an {@code Authorization} header. So the access check runs ONCE (when the
 * ticket is issued, behind the bearer), and the ticket — one asset, one user, a few hours — is what
 * the playlist endpoints accept.
 */
final class PlaybackTickets {

  /** What a valid ticket authorizes: this user watching this asset until then. */
  record Ticket(UUID assetId, UUID userId, Instant expiresAt) {}

  private final SignedTokens tokens;

  PlaybackTickets(String secret) {
    this.tokens = new SignedTokens(secret, "the playback secret");
  }

  String issue(UUID assetId, UUID userId, Instant expiresAt) {
    return tokens.sign(assetId + "." + userId + "." + expiresAt.getEpochSecond());
  }

  /** The ticket, or empty if forged, malformed, expired — or for ANOTHER asset. */
  Optional<Ticket> verify(String token, UUID assetId, Instant now) {
    return tokens
        .verify(token)
        .flatMap(PlaybackTickets::parse)
        .filter(t -> t.assetId().equals(assetId) && now.isBefore(t.expiresAt()));
  }

  private static Optional<Ticket> parse(String payload) {
    String[] parts = payload.split("\\.");
    if (parts.length != 3) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Ticket(
              UUID.fromString(parts[0]),
              UUID.fromString(parts[1]),
              Instant.ofEpochSecond(Long.parseLong(parts[2]))));
    } catch (IllegalArgumentException e) { // bad UUID, bad number
      return Optional.empty();
    }
  }
}
