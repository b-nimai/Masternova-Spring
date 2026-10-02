package com.masternova.java.sealed;

import com.masternova.java.sealed.CouponRule.FlatOff;
import com.masternova.java.sealed.CouponRule.FreeCourse;
import com.masternova.java.sealed.CouponRule.PercentOff;
import com.masternova.java.valueobject.Money;

/**
 * Applies a {@link CouponRule} to a price with one exhaustive switch.
 *
 * <p>The alternative design — an abstract {@code applyTo(Money)} method that each record
 * implements (polymorphism, i.e. the Strategy pattern) — is compared in the study note, §7.
 */
public final class Pricing {

  private Pricing() {}

  public static Money priceAfter(Money price, CouponRule rule) {
    return switch (rule) {
      case PercentOff(int percent) -> price.percentOff(percent);
      // A flat discount bigger than the price makes the course free — never negative.
      // compareTo throws for a currency mismatch: a USD coupon on an INR course is a bug.
      case FlatOff(Money off) when off.compareTo(price) >= 0 -> zeroLike(price);
      case FlatOff(Money off) -> price.minus(off);
      case FreeCourse() -> zeroLike(price);
    };
  }

  private static Money zeroLike(Money price) {
    return new Money(0, price.currency());
  }
}
