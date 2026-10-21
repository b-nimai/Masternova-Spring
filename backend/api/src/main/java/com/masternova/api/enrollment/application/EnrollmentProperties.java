package com.masternova.api.enrollment.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Enrollment and progress tuning, bound from {@code masternova.enrollment.*} (ADR-0022). The
 * schedules ({@code flush-every}, {@code reconcile-every}) are read by their jobs'
 * {@code @Scheduled} placeholders, not here.
 *
 * @param beaconSecret signs beacon tokens (HMAC-SHA256, ≥ 32 bytes; a file secret in the container
 *     stack). Its own key: one leaked key opens one door.
 * @param beaconTtl how long a beacon token works. Every heartbeat (15 s) renews it, so it only has
 *     to outlive the gap between the last heartbeat and the tab closing.
 */
@Validated
@ConfigurationProperties(prefix = "masternova.enrollment")
public record EnrollmentProperties(
    @NotBlank String beaconSecret, @DefaultValue("2m") @NotNull Duration beaconTtl) {}
