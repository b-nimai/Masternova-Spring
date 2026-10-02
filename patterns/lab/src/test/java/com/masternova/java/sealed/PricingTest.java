package com.masternova.java.sealed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.sealed.CouponRule.FlatOff;
import com.masternova.java.sealed.CouponRule.FreeCourse;
import com.masternova.java.sealed.CouponRule.PercentOff;
import com.masternova.java.valueobject.Money;
import org.junit.jupiter.api.Test;

class PricingTest {

  private static final Money PRICE = Money.of(1_000_00, "INR"); // ₹1000.00

  @Test
  void percentOff() {
    assertThat(Pricing.priceAfter(PRICE, new PercentOff(25))).isEqualTo(Money.of(750_00, "INR"));
  }

  @Test
  void flatOff() {
    assertThat(Pricing.priceAfter(PRICE, new FlatOff(Money.of(200_00, "INR"))))
        .isEqualTo(Money.of(800_00, "INR"));
  }

  @Test
  void aFlatDiscountBiggerThanThePriceMakesItFreeNotNegative() {
    assertThat(Pricing.priceAfter(PRICE, new FlatOff(Money.of(5_000_00, "INR"))).isZero())
        .isTrue();
  }

  @Test
  void freeCourse() {
    assertThat(Pricing.priceAfter(PRICE, new FreeCourse()).isZero()).isTrue();
  }

  @Test
  void aCouponInAnotherCurrencyIsRejected() {
    assertThatThrownBy(() -> Pricing.priceAfter(PRICE, new FlatOff(Money.of(10_00, "USD"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rulesValidateThemselves() {
    assertThatThrownBy(() -> new PercentOff(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PercentOff(101)).isInstanceOf(IllegalArgumentException.class);
  }
}
