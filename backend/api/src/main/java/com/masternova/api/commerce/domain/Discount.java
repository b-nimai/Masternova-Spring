package com.masternova.api.commerce.domain;

import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Objects;
import java.util.Optional;

/**
 * How much a coupon takes off a base amount — a STRATEGY per coupon kind (LLD §3, Pricing).
 *
 * <p>⭐ Force: each kind is a different algorithm over the same input (a percentage rounds and caps;
 * a fixed amount clamps), and more kinds are coming. The pricer asks {@link #discountOn} and never
 * switches on a kind column. Sealed: the kinds are a closed set, so the one place that builds a
 * strategy from a row ({@link Coupon#discount()}) is an exhaustive switch.
 *
 * <p>Contract for every strategy: the discount is in the base's currency, never negative and never
 * more than the base, so {@code base − discount} is always a valid {@link Money}.
 */
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "Strategy (sealed): one per coupon kind",
    note = "patterns/docs/01-strategy.md")
public sealed interface Discount {

  Money discountOn(Money base);

  /**
   * {@code percent}% off, rounded half-up to the minor unit, then capped.
   *
   * @param cap the most this coupon may take off, if any ("20% off, up to ₹500")
   */
  record PercentOff(int percent, Optional<Money> cap) implements Discount {
    public PercentOff {
      if (percent < 1 || percent > 100) {
        throw new IllegalArgumentException("percent must be 1..100, was " + percent);
      }
      Objects.requireNonNull(cap, "cap");
    }

    @Override
    public Money discountOn(Money base) {
      // ⭐ Money.percentOff does the rounding in integer arithmetic (no BigDecimal, no double):
      //    20% of ₹999.99 = 19999.8 paise → 20000
      Money off = base.minus(base.percentOff(percent));
      return cap.filter(c -> c.compareTo(off) < 0).orElse(off);
    }
  }

  /** A fixed amount off, never more than the base: ₹500 off a ₹300 course is ₹300 off. */
  record AmountOff(Money amount) implements Discount {
    public AmountOff {
      Objects.requireNonNull(amount, "amount");
      if (amount.isZero()) {
        throw new IllegalArgumentException("an amount-off coupon must take something off");
      }
    }

    @Override
    public Money discountOn(Money base) {
      return amount.compareTo(base) < 0 ? amount : base; // compareTo throws on mixed currencies
    }
  }
}
