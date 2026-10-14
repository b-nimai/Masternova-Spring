package com.masternova.api.commerce.domain;

import static com.masternova.api.commerce.domain.Coupons.NOW;
import static com.masternova.api.commerce.domain.Coupons.inr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.platform.RuleViolationException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Each eligibility rule alone, then the order they're reported in (Specification). */
class CouponEligibilityTest {

  static final UUID COURSE = UUID.randomUUID();

  private static java.util.List<String> failures(Coupon coupon, long subtotal) {
    return CouponEligibility.failures(
            new CouponEligibility.Context(coupon, Set.of(COURSE), inr(subtotal), NOW))
        .stream()
        .map(CouponEligibility.Rule::code)
        .toList();
  }

  @Test
  void aPlainCouponApplies() {
    assertThat(failures(Coupons.coupon(Coupons.percent(10)), 49_900)).isEmpty();
  }

  @Test
  void anInactiveCoupon() {
    Coupon c = Coupons.coupon(Coupons.percent(10));
    c.deactivate();
    assertThat(failures(c, 49_900)).containsExactly("COUPON_INACTIVE");
  }

  @Test
  void theWindowIsStartInclusiveEndExclusive() {
    Duration hour = Duration.ofHours(1);
    assertThat(
            failures(Coupons.coupon(Coupons.window(Coupons.percent(10), NOW.plus(hour), null)), 1))
        .containsExactly("COUPON_NOT_STARTED");
    assertThat(failures(Coupons.coupon(Coupons.window(Coupons.percent(10), NOW, null)), 1))
        .isEmpty(); // starts now: valid
    assertThat(failures(Coupons.coupon(Coupons.window(Coupons.percent(10), null, NOW)), 1))
        .containsExactly("COUPON_EXPIRED"); // ends now: gone
    assertThat(
            failures(Coupons.coupon(Coupons.window(Coupons.percent(10), null, NOW.plus(hour))), 1))
        .isEmpty();
  }

  @Test
  void aUsedUpCoupon() {
    Coupon c = Coupons.coupon(Coupons.limit(Coupons.percent(10), 3));
    assertThat(failures(Coupons.redeemed(c, 2), 1)).isEmpty();
    assertThat(failures(Coupons.redeemed(c, 3), 1)).containsExactly("COUPON_EXHAUSTED");
  }

  @Test
  void aCourseCouponNeedsItsCourseInTheCart() {
    assertThat(failures(Coupons.coupon(Coupons.forCourse(Coupons.percent(10), COURSE)), 1))
        .isEmpty();
    assertThat(
            failures(Coupons.coupon(Coupons.forCourse(Coupons.percent(10), UUID.randomUUID())), 1))
        .containsExactly("COUPON_NOT_APPLICABLE");
  }

  @Test
  void aMinimumSubtotal() {
    Coupon c = Coupons.coupon(Coupons.minimum(Coupons.amount(10_000), 50_000));
    assertThat(failures(c, 49_999)).containsExactly("COUPON_MIN_SUBTOTAL");
    assertThat(failures(c, 50_000)).isEmpty();
  }

  @Test
  void everyFailureIsListedAndTheFirstIsThrown() {
    Coupon c =
        Coupons.coupon(
            Coupons.minimum(
                Coupons.forCourse(
                    Coupons.window(Coupons.percent(10), null, NOW.minusSeconds(1)),
                    UUID.randomUUID()),
                1_000_000));
    c.deactivate();

    assertThat(failures(c, 1))
        .containsExactly(
            "COUPON_INACTIVE", "COUPON_EXPIRED", "COUPON_NOT_APPLICABLE", "COUPON_MIN_SUBTOTAL");
    assertThatThrownBy(
            () ->
                CouponEligibility.require(
                    new CouponEligibility.Context(c, Set.of(COURSE), inr(1), NOW)))
        .isInstanceOf(RuleViolationException.class)
        .extracting(e -> ((RuleViolationException) e).code())
        .isEqualTo("COUPON_INACTIVE");
  }
}
