package com.masternova.patterns.specification.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.patterns.specification.coupon.CouponRules.Context;
import com.masternova.patterns.specification.coupon.CouponRules.Coupon;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CouponRulesTest {

  static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

  @Test
  void aGoodCouponHasNoFailures() {
    Coupon c = new Coupon(true, NOW.plusSeconds(60), 10, 3, "java");
    assertThat(CouponRules.failures(new Context(c, Set.of("java"), NOW))).isEmpty();
  }

  @Test
  void everyFailingRuleIsReportedInOrder() {
    Coupon c = new Coupon(false, NOW, 3, 3, "spring");
    assertThat(CouponRules.failures(new Context(c, Set.of("java"), NOW)))
        .containsExactly(
            "COUPON_INACTIVE", "COUPON_EXPIRED", "COUPON_EXHAUSTED", "COUPON_NOT_APPLICABLE");
  }
}
