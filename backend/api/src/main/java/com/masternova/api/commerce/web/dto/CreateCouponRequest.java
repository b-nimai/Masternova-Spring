package com.masternova.api.commerce.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

/**
 * An admin's new coupon, in the catalog currency. Exactly one of {@code percentOff} and {@code
 * amountOffMinor}; the cap only with a percentage. The entity re-checks the combination.
 */
public record CreateCouponRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,40}") String code,
    @Min(1) @Max(100) Integer percentOff,
    @Positive Long amountOffMinor,
    @Positive Long maxDiscountMinor,
    @PositiveOrZero Long minSubtotalMinor,
    UUID courseId,
    Instant startsAt,
    Instant endsAt,
    @Positive Integer maxRedemptions) {}
