package com.masternova.api.commerce.web.dto;

import com.masternova.api.commerce.domain.Coupon;
import com.masternova.kernel.money.Money;
import java.time.Instant;
import java.util.UUID;

/** A coupon as an admin sees it, redemptions included. */
public record CouponResponse(
    String code,
    Coupon.Kind kind,
    Integer percentOff,
    Long amountOffMinor,
    Long maxDiscountMinor,
    long minSubtotalMinor,
    String currency,
    UUID courseId,
    Instant startsAt,
    Instant endsAt,
    Integer maxRedemptions,
    int redemptions,
    boolean active) {

  public static CouponResponse from(Coupon c) {
    return new CouponResponse(
        c.code(),
        c.kind(),
        c.percentOff().orElse(null),
        c.amountOff().map(Money::amountMinor).orElse(null),
        c.maxDiscount().map(Money::amountMinor).orElse(null),
        c.minSubtotal().amountMinor(),
        c.currency().getCurrencyCode(),
        c.courseId().orElse(null),
        c.startsAt().orElse(null),
        c.endsAt().orElse(null),
        c.maxRedemptions().orElse(null),
        c.redemptions(),
        c.isActive());
  }
}
