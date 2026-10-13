package com.masternova.api.media.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.media.domain.MediaAsset;
import com.masternova.api.media.domain.MediaAssetRepository;
import com.masternova.api.media.domain.MediaStatus;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Playback (ADR-0013, media LLD §4): issue a ticket after ONE access check, then serve the asset's
 * playlists to whoever holds it, rewritten so the media bytes come straight from storage.
 *
 * <p>Who may watch, in Phase 7: the asset's owner or an admin (the instructor previewing). ⭐ Phase
 * 8's entitlement check plugs in at {@link #ticket} — the playlist endpoints never change.
 */
@Service
public class PlaybackService {

  private final MediaAssetRepository assets;
  private final ObjectStorage storage;
  private final PlaybackTickets tickets;
  private final Duration ttl;
  private final Clock clock;

  PlaybackService(
      MediaAssetRepository assets, ObjectStorage storage, MediaProperties props, Clock clock) {
    this.assets = assets;
    this.storage = storage;
    this.tickets = new PlaybackTickets(props.playbackSecret());
    this.ttl = props.playbackTtl();
    this.clock = clock;
  }

  public record Grant(String masterUrl, Instant expiresAt) {}

  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public Grant ticket(CurrentUser me, UUID assetId) {
    MediaAsset asset =
        assets
            .findById(assetId)
            .filter(a -> me.has(Role.ADMIN) || a.isOwnedBy(me.id()))
            .orElseThrow(() -> new NotFoundException("Asset", assetId));
    if (asset.status() != MediaStatus.READY) {
      throw new ConflictException(
          "MEDIA_NOT_READY",
          "This video is still processing.",
          java.util.Map.of("status", asset.status().name()));
    }
    Instant expiresAt = clock.instant().plus(ttl);
    String ticket = tickets.issue(assetId, me.id(), expiresAt);
    return new Grant("/api/v1/media/assets/" + assetId + "/hls/master.m3u8?t=" + ticket, expiresAt);
  }

  /** The master playlist, each rung URI carrying the ticket on to our rung endpoint. */
  public String master(UUID assetId, String ticket) {
    check(assetId, ticket);
    return Playlists.rewrite(
        storage.getText(MediaKeys.master(assetId)), uri -> uri + "?t=" + ticket);
  }

  /** A rung playlist whose init segment and segments are presigned storage urls. */
  public String rung(UUID assetId, String rung, String ticket) {
    PlaybackTickets.Ticket valid = check(assetId, ticket);
    String prefix = MediaKeys.rung(assetId, rung); // validates the rung name (no ../)
    Duration left = Duration.between(clock.instant(), valid.expiresAt());
    return Playlists.rewrite(
        storage.getText(prefix + MediaKeys.PLAYLIST),
        file -> storage.presignDownload(prefix + file, left).toString());
  }

  private PlaybackTickets.Ticket check(UUID assetId, String ticket) {
    return tickets
        .verify(ticket, assetId, clock.instant())
        .orElseThrow(
            () ->
                new ForbiddenException(
                    "PLAYBACK_TICKET_INVALID", "This playback link is invalid or has expired."));
  }
}
