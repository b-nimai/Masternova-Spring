package com.masternova.api.entitlement.web.dto;

import com.masternova.api.entitlement.application.LecturePlaybackService.Ticket;
import java.time.Instant;

/** Where to point the player (a 5-minute, IP-bound link) and until when it works. */
public record LecturePlaybackResponse(String manifestUrl, Instant expiresAt) {

  public static LecturePlaybackResponse from(Ticket t) {
    return new LecturePlaybackResponse(t.manifestUrl(), t.expiresAt());
  }
}
