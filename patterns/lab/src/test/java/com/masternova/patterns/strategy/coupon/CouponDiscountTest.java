package com.masternova.patterns.strategy.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CouponDiscountTest {

  @Test
  void eachKindIsItsOwnAlgorithm() {
    assertThat(CouponDiscount.of("PERCENT", 20, 0).discountOn(99_999)).isEqualTo(20_000);
    assertThat(CouponDiscount.of("PERCENT", 50, 10_000).discountOn(80_000)).isEqualTo(10_000);
    assertThat(CouponDiscount.of("AMOUNT", 50_000, 0).discountOn(30_000)).isEqualTo(30_000);
  }

  @Test
  void theCallerNeverSwitchesOnTheKind() {
    long base = 49_900;
    long total = 0;
    for (CouponDiscount d :
        new CouponDiscount[] {new CouponDiscount.PercentOff(10, 0), new CouponDiscount.AmountOff(500)}) {
      long off = d.discountOn(base); // ⭐ one call, whatever the strategy
      assertThat(off).isBetween(0L, base);
      total += off;
    }
    assertThat(total).isEqualTo(4_990 + 500);
  }
}
