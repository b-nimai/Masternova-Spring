package com.masternova.api.entitlement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.entitlement.application.PlaybackTokens.PlaybackToken;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.media.AssetReadiness;
import com.masternova.api.media.MediaApi;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * A learner watching a lecture (ADR-0017): one entitlement decision issues a 5-minute token bound
 * to user, lecture and IP; the player then fetches the playlists with it, and media serves them.
 *
 * <p>⭐ The decision is not in this class. {@link #issue} carries {@code @PreAuthorize}, so Spring
 * wraps this bean in a PROXY that runs {@code @entitlements.canWatch} before the method body — a
 * denial never reaches it, and comes out as a 403 with its reason. This class is the proxy's
 * RealSubject: it only does the work.
 */
@Service
@DesignPattern(
    value = Pattern.PROXY,
    role = "RealSubject: Spring's @PreAuthorize proxy runs the entitlement check first",
    note = "patterns/docs/15-proxy.md")
public class LecturePlaybackService {

  private final CatalogApi catalog;
  private final MediaApi media;
  private final PlaybackTokens tokens;
  private final EntitlementProperties props;
  private final Clock clock;

  LecturePlaybackService(
      CatalogApi catalog, MediaApi media, EntitlementProperties props, Clock clock) {
    this.catalog = catalog;
    this.media = media;
    this.tokens = new PlaybackTokens(props.playbackSecret());
    this.props = props;
    this.clock = clock;
  }

  /** Where to point the player, and until when that link works. */
  public record Ticket(String manifestUrl, Instant expiresAt) {}

  @PreAuthorize("@entitlements.canWatch(authentication, #lectureId)")
  public Ticket issue(CurrentUser me, UUID lectureId, String clientIp) {
    UUID assetId = playableAsset(lectureId);
    Instant expiresAt = clock.instant().plus(props.tokenTtl());
    String token =
        tokens.issue(new PlaybackToken(lectureId, assetId, me.id(), expiresAt), clientIp);
    return new Ticket(
        "/api/v1/lectures/" + lectureId + "/hls/master.m3u8?token=" + token, expiresAt);
  }

  /** The master playlist; each rung URI carries the same token on to our rung route. */
  public String master(UUID lectureId, String token, String clientIp) {
    PlaybackToken valid = check(lectureId, token, clientIp);
    String query = "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    return media.masterPlaylist(valid.assetId(), uri -> uri + query);
  }

  /** A rung playlist whose segments are presigned storage urls (30 minutes, ADR-0017). */
  public String rung(UUID lectureId, String rung, String token, String clientIp) {
    PlaybackToken valid = check(lectureId, token, clientIp);
    return media.rungPlaylist(valid.assetId(), rung, props.segmentUrlTtl());
  }

  /** The lecture's video, if it has one and it finished processing. */
  private UUID playableAsset(UUID lectureId) {
    UUID assetId =
        catalog
            .courseFactsOfLecture(lectureId)
            .flatMap(c -> c.lecture(lectureId))
            .orElseThrow(() -> new NotFoundException("Lecture", lectureId))
            .video()
            .orElseThrow(
                () ->
                    new ConflictException(
                        "LECTURE_HAS_NO_VIDEO", "This lecture has no video.", Map.of()));
    AssetReadiness readiness = media.readiness(Set.of(assetId)).get(assetId);
    if (readiness == null || !readiness.ready()) {
      throw new ConflictException(
          "MEDIA_NOT_READY", "This video is still processing.", Map.of("lectureId", lectureId));
    }
    return assetId;
  }

  private PlaybackToken check(UUID lectureId, String token, String clientIp) {
    return tokens
        .verify(token, lectureId, clientIp, clock.instant())
        .orElseThrow(
            () ->
                new ForbiddenException(
                    "PLAYBACK_TOKEN_INVALID",
                    "This playback link is invalid or has expired. Ask for a new one."));
  }
}
