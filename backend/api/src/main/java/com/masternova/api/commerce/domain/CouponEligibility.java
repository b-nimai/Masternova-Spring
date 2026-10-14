package com.masternova.api.commerce.domain;

import com.masternova.api.platform.RuleViolationException;
import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Whether a coupon applies to a cart: a SPECIFICATION that explains itself, the AND of named rules
 * (LLD §3, Pricing). Same shape as catalog's {@code PublishGate}.
 *
 * <p>⭐ Force: six independent rules today, each with its own error code the UI shows ("expired",
 * "add ₹200 more"), and more coming ("first purchase only"). Adding a rule is adding an entry to
 * {@link #RULES}; the pricer just asks {@link #require}. Pure: every rule is unit-tested alone.
 */
@DesignPattern(
    value = Pattern.SPECIFICATION,
    role = "Composite of coded rules (explainable)",
    note = "patterns/docs/08-specification.md")
public final class CouponEligibility {

  /** Everything a rule may look at. The rules never query. */
  public record Context(Coupon coupon, Set<UUID> courseIds, Money subtotal, Instant now) {
    public Context {
      Objects.requireNonNull(coupon, "coupon");
      courseIds = Set.copyOf(courseIds);
      Objects.requireNonNull(subtotal, "subtotal");
      Objects.requireNonNull(now, "now");
    }
  }

  /** One rule: a stable code, the sentence for people, and the predicate. */
  public record Rule(String code, String message, Predicate<Context> holds) {}

  /** In the order they're checked: the first failure is the one reported. */
  public static final List<Rule> RULES =
      List.of(
          new Rule(
              "COUPON_INACTIVE", "This coupon is no longer offered.", c -> c.coupon.isActive()),
          new Rule(
              "COUPON_NOT_STARTED",
              "This coupon isn't valid yet.",
              c -> c.coupon.startsAt().map(s -> !c.now.isBefore(s)).orElse(true)),
          new Rule(
              "COUPON_EXPIRED",
              "This coupon has expired.",
              c -> c.coupon.endsAt().map(c.now::isBefore).orElse(true)),
          new Rule(
              "COUPON_EXHAUSTED",
              "This coupon has been used up.",
              c -> c.coupon.maxRedemptions().map(max -> c.coupon.redemptions() < max).orElse(true)),
          new Rule(
              "COUPON_NOT_APPLICABLE",
              "This coupon is for a course that isn't in your cart.",
              c -> c.coupon.courseId().map(c.courseIds::contains).orElse(true)),
          new Rule(
              "COUPON_MIN_SUBTOTAL",
              "Your cart is below this coupon's minimum.",
              c -> c.subtotal.compareTo(c.coupon.minSubtotal()) >= 0));

  private CouponEligibility() {}

  /** Every failing rule, in order — empty means the coupon applies. */
  public static List<Rule> failures(Context context) {
    return RULES.stream().filter(r -> !r.holds().test(context)).toList();
  }

  /** Throws 422 with the FIRST failing rule's code (and the coupon's code as a detail). */
  public static void require(Context context) {
    failures(context).stream()
        .findFirst()
        .ifPresent(
            r -> {
              throw new RuleViolationException(
                  r.code(), r.message(), Map.of("couponCode", context.coupon.code()));
            });
  }
}
