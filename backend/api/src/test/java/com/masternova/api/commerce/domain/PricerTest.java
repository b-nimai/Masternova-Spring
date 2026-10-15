package com.masternova.api.commerce.domain;

import static com.masternova.api.commerce.domain.Coupons.NOW;
import static com.masternova.api.commerce.domain.Coupons.inr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseOffer;
import com.masternova.api.platform.DomainException;
import com.masternova.api.platform.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The cart rules and the totals, with offers built in memory (no catalog, no database). */
class PricerTest {

  static final UUID BUYER = UUID.randomUUID();
  static final UUID TEACHER = UUID.randomUUID();
  final CourseOffer java = offer("Java", 49_900);
  final CourseOffer spring = offer("Spring", 79_900);

  static CourseOffer offer(String title, long price) {
    return new CourseOffer(
        UUID.randomUUID(),
        title.toLowerCase(),
        title,
        TEACHER,
        Availability.PUBLISHED,
        inr(price),
        true);
  }

  private Quote price(List<CourseOffer> offers, Optional<Coupon> coupon) {
    return Pricer.price(
        new Pricer.Cart(
            BUYER, offers.stream().map(CourseOffer::courseId).toList(), offers, Set.of()),
        coupon,
        NOW);
  }

  private static String code(Runnable pricing) {
    try {
      pricing.run();
    } catch (DomainException e) {
      return e.code();
    }
    throw new AssertionError("expected a rule violation");
  }

  @Test
  void addsTheLinesInCartOrder() {
    Quote q = price(List.of(spring, java), Optional.empty());

    assertThat(q.lines()).extracting(Quote.Line::title).containsExactly("Spring", "Java");
    assertThat(q.subtotal()).isEqualTo(inr(129_800));
    assertThat(q.discount()).isEqualTo(inr(0));
    assertThat(q.total()).isEqualTo(inr(129_800));
    assertThat(q.couponCode()).isEmpty();
  }

  @Test
  void aCartCouponDiscountsTheSubtotal() {
    Quote q = price(List.of(java, spring), Optional.of(Coupons.coupon(Coupons.percent(10))));

    assertThat(q.discount()).isEqualTo(inr(12_980));
    assertThat(q.total()).isEqualTo(inr(116_820));
    assertThat(q.couponCode()).contains("SAVE");
  }

  @Test
  void aCourseCouponDiscountsOnlyItsLine() {
    Coupon half = Coupons.coupon(Coupons.forCourse(Coupons.percent(50), java.courseId()));

    assertThat(price(List.of(java, spring), Optional.of(half)).discount())
        .isEqualTo(inr(24_950)); // half of Java only, not of the whole cart
  }

  @Test
  void aHundredPercentCouponMakesAFreeOrder() {
    Quote q = price(List.of(java), Optional.of(Coupons.coupon(Coupons.percent(100))));
    assertThat(q.total()).isEqualTo(inr(0));
    assertThat(q.isFree()).isTrue();
  }

  @Test
  void anIneligibleCouponIsA422() {
    Coupon min = Coupons.coupon(Coupons.minimum(Coupons.amount(1_000), 1_000_000));
    assertThat(code(() -> price(List.of(java), Optional.of(min)))).isEqualTo("COUPON_MIN_SUBTOTAL");
  }

  @Test
  void onlyCoursesOnSale() {
    CourseOffer draft =
        new CourseOffer(
            UUID.randomUUID(), "d", "Draft", TEACHER, Availability.UNPUBLISHED, inr(100), true);
    CourseOffer unpriced =
        new CourseOffer(
            UUID.randomUUID(), "u", "Unpriced", TEACHER, Availability.PUBLISHED, inr(0), false);
    CourseOffer free = offer("Free", 0);

    assertThat(code(() -> price(List.of(java, draft), Optional.empty())))
        .isEqualTo("COURSE_NOT_FOR_SALE");
    assertThat(code(() -> price(List.of(unpriced), Optional.empty())))
        .isEqualTo("COURSE_NOT_FOR_SALE");
    assertThat(code(() -> price(List.of(free), Optional.empty()))).isEqualTo("COURSE_IS_FREE");
  }

  @Test
  void notWhatTheBuyerAlreadyHas() {
    List<UUID> ids = List.of(java.courseId());
    assertThat(
            code(
                () ->
                    Pricer.price(
                        new Pricer.Cart(BUYER, ids, List.of(java), Set.of(java.courseId())),
                        Optional.empty(),
                        NOW)))
        .isEqualTo("COURSE_ALREADY_OWNED");
    assertThat( // an instructor can't buy their own course
            code(
                () ->
                    Pricer.price(
                        new Pricer.Cart(TEACHER, ids, List.of(java), Set.of()),
                        Optional.empty(),
                        NOW)))
        .isEqualTo("COURSE_ALREADY_OWNED");
  }

  @Test
  void anUnknownCourseIsA404() {
    assertThatThrownBy(
            () ->
                Pricer.price(
                    new Pricer.Cart(BUYER, List.of(UUID.randomUUID()), List.of(), Set.of()),
                    Optional.empty(),
                    NOW))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void theCartShape() {
    UUID a = UUID.randomUUID();
    assertThat(code(() -> Pricer.requireCartShape(List.of(), 20))).isEqualTo("CART_EMPTY");
    assertThat(code(() -> Pricer.requireCartShape(List.of(a, UUID.randomUUID()), 1)))
        .isEqualTo("CART_TOO_LARGE");
    assertThat(code(() -> Pricer.requireCartShape(List.of(a, a), 20)))
        .isEqualTo("CART_DUPLICATE_COURSE");
  }
}
