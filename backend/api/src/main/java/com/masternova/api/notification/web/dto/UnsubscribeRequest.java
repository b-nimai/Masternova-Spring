package com.masternova.api.notification.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UnsubscribeRequest(@NotBlank String token) {}
