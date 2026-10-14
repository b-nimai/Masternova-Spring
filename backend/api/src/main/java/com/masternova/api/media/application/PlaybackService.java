package com.masternova.api.media.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.media.domain.MediaAsset;
import com.masternova.api.media.domain.MediaAssetRepository;
import com.masternova.api.media.domain.MediaStatus;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Playback (ADR-0013, media LLD §4): issue a ticket after ONE access check, then serve the asset's
 * playlists to whoever holds it, rewritten so the media bytes come straight from storage.
 *
 * <p>Who may watch: the asset's owner or an admin — the instructor previewing an upload. Learners
 * watch through entitlement's lecture playback (ADR-0017), on the same {@link HlsDelivery}.
 */
@Service
public class PlaybackService {

  private final MediaAssetRepository assets;
  private final HlsDelivery hls;
  private final PlaybackTickets tickets;
  private final Duration ttl;
  private final Clock clock;

  PlaybackService(
      MediaAssetRepository assets, HlsDelivery hls, MediaProperties props, Clock clock) {
    this.assets = assets;
    this.hls = hls;
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
            .filter(a -> a.isVisibleTo(me.id(), me.has(Role.ADMIN)))
            .orElseThrow(() -> new NotFoundException("Asset", assetId));
    if (asset.status() != MediaStatus.READY) {
      throw new ConflictException(
          "MEDIA_NOT_READY",
          "This video is still processing.",
          Map.of("status", asset.status().name()));
    }
    Instant expiresAt = clock.instant().plus(ttl);
    String ticket = tickets.issue(assetId, me.id(), expiresAt);
    return new Grant("/api/v1/media/assets/" + assetId + "/hls/master.m3u8?t=" + ticket, expiresAt);
  }

  /** The master playlist, each rung URI carrying the ticket on to our rung endpoint. */
  public String master(UUID assetId, String ticket) {
    check(assetId, ticket);
    return hls.master(
        assetId, uri -> uri + "?t=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
  }

  /** A rung playlist whose init segment and segments are presigned storage urls. */
  public String rung(UUID assetId, String rung, String ticket) {
    PlaybackTickets.Ticket valid = check(assetId, ticket);
    return hls.rung(assetId, rung, Duration.between(clock.instant(), valid.expiresAt()));
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
