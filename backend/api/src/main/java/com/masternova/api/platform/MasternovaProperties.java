package com.masternova.api.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Masternova's own settings, bound from {@code masternova.*} in application.yaml / env vars.
 *
 * <p>⭐ Typed, validated, immutable configuration — read in ONE place and injected where needed,
 * instead of {@code @Value("${...}")} strings scattered through the code. A bad value (say {@code
 * cart-max-items: 0}) fails STARTUP, not the first checkout.
 *
 * <p>Study note: {@code patterns/java/08-spring-ioc-and-di.md} §8.
 *
 * @param webUrl the public URL of the Angular app (links in emails, CORS)
 * @param checkout checkout rules
 * @param outbox transactional outbox relay tuning (Phase 2.4)
 */
@Validated
@ConfigurationProperties(prefix = "masternova")
public record MasternovaProperties(
    @NotNull URI webUrl,
    // ⭐ an EMPTY @DefaultValue on a nested record = "build it from its own defaults when no
    //    masternova.checkout.* keys are set". Without it the nested record binds as null.
    @Valid @DefaultValue Checkout checkout,
    @Valid @DefaultValue Outbox outbox) {

  /**
   * @param cartMaxItems the most courses one cart can hold (yaml: {@code cart-max-items})
   * @param paymentTimeout how long to wait for the payment provider (yaml: {@code 10s})
   */
  public record Checkout(
      @DefaultValue("20") @Min(1) @Max(100) int cartMaxItems,
      @DefaultValue("10s") @NotNull Duration paymentTimeout) {}

  /**
   * @param relayEnabled run the scheduled relay in this process (switched off in tests that drive
   *     the relay by hand)
   * @param pollInterval pause between relay batches
   * @param batchSize rows claimed per batch
   * @param lease how long a claimed row is reserved before another relay may take it over
   * @param maxAttempts deliveries before a message is parked as DEAD
   * @param baseBackoff first retry delay; doubles per attempt up to {@code maxBackoff}
   * @param maxBackoff ceiling for the retry delay
   */
  public record Outbox(
      @DefaultValue("true") boolean relayEnabled,
      @DefaultValue("1s") @NotNull Duration pollInterval,
      @DefaultValue("50") @Min(1) @Max(1000) int batchSize,
      @DefaultValue("60s") @NotNull Duration lease,
      @DefaultValue("10") @Min(1) int maxAttempts,
      @DefaultValue("1s") @NotNull Duration baseBackoff,
      @DefaultValue("1h") @NotNull Duration maxBackoff) {}
}
