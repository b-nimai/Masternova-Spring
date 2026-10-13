package com.masternova.api.media.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Upload tuning, bound from {@code masternova.media.*}.
 *
 * @param partUrlTtl how long a presigned part url works. A slow 10 GiB upload outlives it; the
 *     uploader then asks {@code GET /uploads/{id}} for fresh urls.
 * @param sessionTtl how long an upload may stay open before the sweeper aborts it (7.6)
 * @param allowedContentTypes the declared type must be one of these. A first filter only: the
 *     worker's probe checks the real container.
 */
@Validated
@ConfigurationProperties(prefix = "masternova.media")
public record MediaProperties(
    @DefaultValue("1h") @NotNull Duration partUrlTtl,
    @DefaultValue("24h") @NotNull Duration sessionTtl,
    @DefaultValue({"video/mp4", "video/quicktime", "video/webm", "video/x-matroska"}) @NotEmpty
        Set<String> allowedContentTypes) {}
