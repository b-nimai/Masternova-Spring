package com.masternova.worker.pipeline;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The transcode pipeline's tuning, bound from {@code masternova.pipeline.*} (video-pipeline LLD).
 *
 * @param enabled run the job poller in this process (tests that drive jobs by hand turn it off)
 * @param ladder which {@code LadderProfile}: {@code standard} (prod) or {@code fast} (dev, CI)
 * @param slots jobs run at once in this process. ffmpeg is CPU-bound and multi-threaded: more slots
 *     than ~cores/2 only makes every job slower
 * @param lease how long a claimed job is reserved; a worker that stops heart-beating loses it
 * @param heartbeat how often a running job extends its lease (well under {@code lease})
 * @param maxAttempts runs before a job is DEAD (permanent failures are DEAD at once)
 * @param baseBackoff first retry delay; × 4 per attempt (30 s, 2 m, 8 m, 32 m)
 */
@Validated
@ConfigurationProperties(prefix = "masternova.pipeline")
public record PipelineProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("standard") @NotBlank String ladder,
    @DefaultValue("ffmpeg") @NotBlank String ffmpegPath,
    @DefaultValue("ffprobe") @NotBlank String ffprobePath,
    @DefaultValue("/tmp/pipeline") @NotNull Path workDir,
    @DefaultValue("2") @Min(1) int slots,
    @DefaultValue("2s") @NotNull Duration pollInterval,
    @DefaultValue("2m") @NotNull Duration lease,
    @DefaultValue("30s") @NotNull Duration heartbeat,
    @DefaultValue("5") @Min(1) int maxAttempts,
    @DefaultValue("30s") @NotNull Duration baseBackoff,
    @DefaultValue("6h") @NotNull Duration sourceUrlTtl) {}
