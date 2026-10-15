package com.masternova.patterns.strategy.coupon;

/**
 * The coupon kinds as a SEALED Strategy (Phase 9.2, simplified from {@code
 * com.masternova.api.commerce.domain.Discount}): each kind is an algorithm over the same base, all
 * in paise. Unlike payment providers, these strategies are DATA (records), not beans: the row picks
 * one, so the factory is a switch over a closed set.
 */
public sealed interface CouponDiscount {

  /** Contract: 0 ≤ result ≤ base. */
  long discountOn(long baseMinor);

  record PercentOff(int percent, long capMinor) implements CouponDiscount {
    public PercentOff {
      if (percent < 1 || percent > 100) {
        throw new IllegalArgumentException("percent must be 1..100");
      }
    }

    @Override
    public long discountOn(long baseMinor) {
      long off = (baseMinor * percent + 50) / 100; // integer half-up: no double, no BigDecimal
      return capMinor > 0 ? Math.min(off, capMinor) : off;
    }
  }

  record AmountOff(long amountMinor) implements CouponDiscount {
    @Override
    public long discountOn(long baseMinor) {
      return Math.min(amountMinor, baseMinor);
    }
  }

  /** The one place a stored kind becomes a strategy — exhaustive, so a new kind can't be missed. */
  static CouponDiscount of(String kind, long value, long capMinor) {
    return switch (kind) {
      case "PERCENT" -> new PercentOff((int) value, capMinor);
      case "AMOUNT" -> new AmountOff(value);
      default -> throw new IllegalArgumentException("unknown coupon kind " + kind);
    };
  }
}
