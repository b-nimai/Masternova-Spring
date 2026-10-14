package com.masternova.patterns.specification.coupon;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An EXPLAINABLE specification (Phase 9.2, simplified from {@code
 * com.masternova.api.commerce.domain.CouponEligibility}): the AND of coded rules that reports which
 * rules failed, in order, so the first one becomes the API's error code.
 */
public final class CouponRules {

  public record Coupon(
      boolean active, Instant endsAt, Integer maxRedemptions, int redemptions, String courseId) {}

  public record Context(Coupon coupon, Set<String> cart, Instant now) {}

  public record Rule(String code, Predicate<Context> holds) {}

  public static final List<Rule> RULES =
      List.of(
          new Rule("COUPON_INACTIVE", c -> c.coupon().active()),
          new Rule(
              "COUPON_EXPIRED", c -> c.coupon().endsAt() == null || c.now().isBefore(c.coupon().endsAt())),
          new Rule(
              "COUPON_EXHAUSTED",
              c ->
                  c.coupon().maxRedemptions() == null
                      || c.coupon().redemptions() < c.coupon().maxRedemptions()),
          new Rule(
              "COUPON_NOT_APPLICABLE",
              c -> c.coupon().courseId() == null || c.cart().contains(c.coupon().courseId())));

  private CouponRules() {}

  /** Collects instead of short-circuiting: every failing code, in rule order. */
  public static List<String> failures(Context context) {
    return RULES.stream().filter(r -> !r.holds().test(context)).map(Rule::code).toList();
  }
}
