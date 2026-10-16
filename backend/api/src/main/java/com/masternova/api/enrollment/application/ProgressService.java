package com.masternova.api.enrollment.application;

import com.masternova.api.enrollment.domain.ProgressStore;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import com.masternova.api.platform.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The player's side of progress: heartbeats and beacons in. ⭐ No SQL on this path — the store is
 * the write-back buffer (ADR-0022); whether the lecture exists and the learner is enrolled is
 * checked at the flush, once per batch.
 */
@Service
public class ProgressService {

  /** What a heartbeat answers: the credential for the page's last beacon. */
  public record Heartbeat(String beaconToken, Instant beaconExpiresAt) {}

  private final ProgressStore store;
  private final BeaconTokens beacons;
  private final EnrollmentProperties props;
  private final Clock clock;

  ProgressService(ProgressStore store, EnrollmentProperties props, Clock clock) {
    this.store = store;
    this.beacons = new BeaconTokens(props.beaconSecret());
    this.props = props;
    this.clock = clock;
  }

  /** A heartbeat from the signed-in player; answers a fresh beacon token for this lecture. */
  public Heartbeat heartbeat(UUID userId, UUID lectureId, int positionSeconds, String clientIp) {
    Instant now = clock.instant();
    store.record(ProgressUpdate.heartbeat(userId, lectureId, positionSeconds, now));
    Instant expiresAt = now.plus(props.beaconTtl());
    return new Heartbeat(beacons.issue(userId, lectureId, expiresAt, clientIp), expiresAt);
  }

  /**
   * The last word of a closing page. Same effect as a heartbeat, authorised by the token in its
   * body: 403 {@code BEACON_TOKEN_INVALID} if forged, expired or sent from another network.
   */
  public void beacon(String token, int positionSeconds, String clientIp) {
    Instant now = clock.instant();
    BeaconTokens.Beacon beacon =
        beacons
            .verify(token, clientIp, now)
            .orElseThrow(
                () ->
                    new ForbiddenException(
                        "BEACON_TOKEN_INVALID", "This progress beacon is invalid or has expired."));
    store.record(
        ProgressUpdate.heartbeat(beacon.userId(), beacon.lectureId(), positionSeconds, now));
  }
}
