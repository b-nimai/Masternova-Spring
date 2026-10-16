package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.domain.ProgressUpdate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A closing page's last position, with the beacon token its last heartbeat answered. */
public record BeaconRequest(
    @NotBlank @Size(max = 512) String token,
    @NotNull @Min(0) @Max(ProgressUpdate.MAX_POSITION_SECONDS) Integer positionSeconds) {}
