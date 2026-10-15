package com.masternova.api.commerce.domain;

import static com.masternova.api.commerce.domain.Coupons.inr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.kernel.money.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Each coupon kind's algorithm alone (Strategy): no Spring, no database. */
class DiscountTest {

  @ParameterizedTest(name = "{0}% of {1} = {2}")
  @CsvSource({
    "20, 49900, 9980",
    "20, 99999, 20000", // 19999.8 paise rounds half-up
    "15, 33333, 5000", // 4999.95 → 5000
    "10, 5, 1", // 0.5 paise → 1
    "100, 49900, 49900",
    "1, 49, 0" // 0.49 paise → 0
  })
  void percentOffRoundsHalfUpToThePaisa(int percent, long base, long expected) {
    assertThat(new Discount.PercentOff(percent, Optional.empty()).discountOn(inr(base)))
        .isEqualTo(inr(expected));
  }

  @Test
  void percentOffIsCapped() {
    Discount d = new Discount.PercentOff(50, Optional.of(inr(50_000)));

    assertThat(d.discountOn(inr(80_000))).isEqualTo(inr(40_000)); // under the cap
    assertThat(d.discountOn(inr(200_000))).isEqualTo(inr(50_000)); // capped
  }

  @Test
  void amountOffNeverExceedsTheBase() {
    Discount d = new Discount.AmountOff(inr(50_000));

    assertThat(d.discountOn(inr(80_000))).isEqualTo(inr(50_000));
    assertThat(d.discountOn(inr(30_000))).isEqualTo(inr(30_000)); // ₹500 off ₹300 = ₹300 off
  }

  @Test
  void theContractHoldsForEveryKind() {
    // ⭐ base − discount is always a valid Money: never negative, same currency
    for (Discount d :
        new Discount[] {
          new Discount.PercentOff(100, Optional.empty()),
          new Discount.PercentOff(33, Optional.of(inr(1))),
          new Discount.AmountOff(inr(1_000_000))
        }) {
      for (long base : new long[] {0, 1, 99, 49_900}) {
        Money off = d.discountOn(inr(base));
        assertThat(off.compareTo(inr(base))).isLessThanOrEqualTo(0);
        assertThat(inr(base).minus(off).currency()).isEqualTo(Coupons.INR);
      }
    }
  }

  @Test
  void aStrategyRejectsWhatCouldNeverApply() {
    assertThatThrownBy(() -> new Discount.PercentOff(0, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Discount.PercentOff(101, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Discount.AmountOff(inr(0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Discount.AmountOff(Money.of(100, "USD")).discountOn(inr(500)))
        .isInstanceOf(IllegalArgumentException.class); // ₹ vs $: a bug, not a discount
  }

  @Test
  void aCouponRowBecomesItsStrategy() {
    assertThat(Coupons.coupon(Coupons.capped(Coupons.percent(20), 10_000)).discount())
        .isEqualTo(new Discount.PercentOff(20, Optional.of(inr(10_000))));
    assertThat(Coupons.coupon(Coupons.amount(5_000)).discount())
        .isEqualTo(new Discount.AmountOff(inr(5_000)));
  }

  @Test
  void aCouponIsEitherAPercentageOrAnAmount() {
    var both =
        new Coupon.Terms(
            Optional.of(10),
            Optional.of(inr(100)),
            Optional.empty(),
            inr(0),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    assertThatThrownBy(() -> Coupons.coupon(both)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Coupon.create("x", Coupons.percent(10), Coupons.INR, Coupons.NOW))
        .hasMessageContaining("3–40");
    // review fix: V18's CHECK forbids a cap on an amount coupon — refuse it here (a 400), not as a
    // 500
    assertThatThrownBy(() -> Coupons.coupon(Coupons.capped(Coupons.amount(500), 100)))
        .hasMessageContaining("only a percentage coupon has a cap");
    assertThat(Coupon.create(" save20 ", Coupons.percent(20), Coupons.INR, Coupons.NOW).code())
        .isEqualTo("SAVE20");
  }
}
