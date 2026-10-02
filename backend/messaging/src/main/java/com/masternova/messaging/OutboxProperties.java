package com.masternova.messaging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox tuning, bound from {@code masternova.outbox.*} (the same keys the api used in Phase 2).
 *
 * @param relayEnabled run the relay in THIS process. ⭐ Off by default: exactly one deployable
 *     relays (the worker), because the relay marks events with no local handler as DONE — a second
 *     relay elsewhere would swallow them (ADR-0008).
 * @param pollInterval pause between relay batches
 * @param batchSize rows claimed per batch
 * @param lease how long a claimed row is reserved before another relay may take it over
 * @param maxAttempts deliveries before a message is parked as DEAD
 * @param baseBackoff first retry delay; doubles per attempt up to {@code maxBackoff}
 * @param maxBackoff ceiling for the retry delay
 */
@Validated
@ConfigurationProperties(prefix = "masternova.outbox")
public record OutboxProperties(
    @DefaultValue("false") boolean relayEnabled,
    @DefaultValue("1s") @NotNull Duration pollInterval,
    @DefaultValue("50") @Min(1) @Max(1000) int batchSize,
    @DefaultValue("60s") @NotNull Duration lease,
    @DefaultValue("10") @Min(1) int maxAttempts,
    @DefaultValue("1s") @NotNull Duration baseBackoff,
    @DefaultValue("1h") @NotNull Duration maxBackoff) {}
