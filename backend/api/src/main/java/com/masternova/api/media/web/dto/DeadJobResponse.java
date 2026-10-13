package com.masternova.api.media.web.dto;

import com.masternova.api.media.domain.DeadJobs.DeadJob;
import java.time.Instant;
import java.util.UUID;

public record DeadJobResponse(
    UUID id, UUID assetId, String type, String rung, int attempts, String lastError, Instant at) {

  public static DeadJobResponse from(DeadJob j) {
    return new DeadJobResponse(
        j.id(), j.assetId(), j.type(), j.rung(), j.attempts(), j.lastError(), j.at());
  }
}
