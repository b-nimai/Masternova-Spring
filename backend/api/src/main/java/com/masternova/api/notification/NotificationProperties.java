package com.masternova.api.notification;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code masternova.notification.*}.
 *
 * @param tokenSecret HMAC key that verifies unsubscribe links — the SAME value the worker signs
 *     with (file secret {@code NOTIFICATION_TOKEN_SECRET})
 */
@Validated
@ConfigurationProperties(prefix = "masternova.notification")
public record NotificationProperties(@NotBlank String tokenSecret) {}
