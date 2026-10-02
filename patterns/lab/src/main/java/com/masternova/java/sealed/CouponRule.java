package com.masternova.java.sealed;

import com.masternova.java.valueobject.Money;
import java.util.Objects;

/**
 * How a coupon changes a course price. A closed set of rule SHAPES, each with different data —
 * the case for a sealed interface rather than an enum.
 */
// ⭐ Why not an enum? An enum is a fixed set of INSTANCES that all share one shape. Here
//    PercentOff carries an int, FlatOff carries Money, FreeCourse carries nothing — different
//    shapes, so each is its own record type in a sealed family.
public sealed interface CouponRule {

  record PercentOff(int percent) implements CouponRule {
    public PercentOff {
      if (percent < 1 || percent > 100) {
        throw new IllegalArgumentException("percent must be 1..100, was " + percent);
      }
    }
  }

  record FlatOff(Money amount) implements CouponRule {
    public FlatOff {
      Objects.requireNonNull(amount, "amount");
    }
  }

  record FreeCourse() implements CouponRule {}
}
