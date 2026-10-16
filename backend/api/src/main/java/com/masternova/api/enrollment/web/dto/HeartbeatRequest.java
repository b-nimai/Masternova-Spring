package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.domain.ProgressUpdate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Where the player is, in whole seconds. */
public record HeartbeatRequest(
    @NotNull @Min(0) @Max(ProgressUpdate.MAX_POSITION_SECONDS) Integer positionSeconds) {}
