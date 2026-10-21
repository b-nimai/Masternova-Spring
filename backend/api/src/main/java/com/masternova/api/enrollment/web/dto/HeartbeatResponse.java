package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.application.ProgressService.Heartbeat;
import java.time.Instant;

/** The beacon token to use if the page closes before the next heartbeat. */
public record HeartbeatResponse(String beaconToken, Instant beaconExpiresAt) {

  public static HeartbeatResponse from(Heartbeat h) {
    return new HeartbeatResponse(h.beaconToken(), h.beaconExpiresAt());
  }
}
