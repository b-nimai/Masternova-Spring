package com.masternova.api.notification.web.dto;

import jakarta.validation.constraints.NotNull;

/** {@code Boolean}, not {@code boolean}: a missing field must be a 400, not a silent "false". */
public record UpdatePreferenceRequest(@NotNull Boolean enabled) {}
