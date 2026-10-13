package com.masternova.api.media.web.dto;

import com.masternova.api.media.application.PlaybackService.Grant;
import java.time.Instant;

/** Where to point the player, and until when that works. */
public record PlaybackResponse(String masterUrl, Instant expiresAt) {

  public static PlaybackResponse from(Grant g) {
    return new PlaybackResponse(g.masterUrl(), g.expiresAt());
  }
}
