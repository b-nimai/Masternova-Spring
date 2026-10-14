package com.masternova.api.entitlement.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Entitlement tuning, bound from {@code masternova.entitlement.*}.
 *
 * @param cacheTtl how long a cached entitlement row lives: the BACKSTOP for a lost eviction, not
 *     the invalidation (ADR-0016)
 * @param playbackSecret signs learner playback tokens (HMAC-SHA256, ≥ 32 bytes; a file secret in
 *     the container stack). Separate from media's owner-preview secret: one leaked key opens one
 *     door.
 * @param tokenTtl how long a playback token works — the oldest a decision can be when a playlist is
 *     fetched (ADR-0017)
 * @param segmentUrlTtl how long the presigned segment urls in a rung playlist work: a pause longer
 *     than this costs the player one re-ticket
 */
@Validated
@ConfigurationProperties(prefix = "masternova.entitlement")
public record EntitlementProperties(
    @DefaultValue("5m") @NotNull Duration cacheTtl,
    @NotBlank String playbackSecret,
    @DefaultValue("5m") @NotNull Duration tokenTtl,
    @DefaultValue("30m") @NotNull Duration segmentUrlTtl) {}
