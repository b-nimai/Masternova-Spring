package com.masternova.api.commerce.domain;

import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseOffer;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.kernel.money.Money;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prices a cart (LLD §3, Pricing): checks every course is for sale to this buyer, adds the prices,
 * applies the coupon. ⭐ Pure — the application layer fetches the offers, the buyer's held courses
 * and the coupon, and passes them in; every rule here is unit-tested without Spring.
 */
public final class Pricer {

  private Pricer() {}

  /** What the pricer needs about the buyer and the cart, fetched once by the caller. */
  public record Cart(
      UUID buyerId, List<UUID> courseIds, List<CourseOffer> offers, Set<UUID> heldCourses) {
    public Cart {
      courseIds = List.copyOf(courseIds);
      offers = List.copyOf(offers);
      heldCourses = Set.copyOf(heldCourses);
    }
  }

  public static Quote price(Cart cart, Optional<Coupon> coupon, Instant now) {
    Map<UUID, CourseOffer> byId =
        cart.offers().stream()
            .collect(Collectors.toMap(CourseOffer::courseId, Function.identity()));
    List<Quote.Line> lines = cart.courseIds().stream().map(id -> line(cart, byId, id)).toList();

    Money subtotal = lines.stream().map(Quote.Line::price).reduce(Money::plus).orElseThrow();
    Money discount = coupon.map(c -> discount(c, lines, subtotal, now)).orElse(zero(subtotal));
    return new Quote(lines, subtotal, discount, subtotal.minus(discount), coupon.map(Coupon::code));
  }

  /** Cart-shape checks the request's annotations can't do (the limit is configuration). */
  public static void requireCartShape(List<UUID> courseIds, int maxItems) {
    if (courseIds.isEmpty()) {
      throw new RuleViolationException("CART_EMPTY", "Add a course to your cart first.");
    }
    if (courseIds.size() > maxItems) {
      throw new RuleViolationException(
          "CART_TOO_LARGE",
          "A cart holds at most " + maxItems + " courses.",
          Map.of("maxItems", maxItems));
    }
    if (new HashSet<>(courseIds).size() != courseIds.size()) {
      throw new RuleViolationException("CART_DUPLICATE_COURSE", "Each course can be bought once.");
    }
  }

  private static Quote.Line line(Cart cart, Map<UUID, CourseOffer> offers, UUID courseId) {
    CourseOffer offer = offers.get(courseId);
    if (offer == null) {
      throw new NotFoundException("Course", courseId);
    }
    Map<String, Object> details = Map.of("courseId", courseId);
    if (offer.availability() != Availability.PUBLISHED || !offer.priceSet()) {
      throw new RuleViolationException(
          "COURSE_NOT_FOR_SALE", "“" + offer.title() + "” isn't on sale.", details);
    }
    if (offer.isFree()) {
      throw new RuleViolationException(
          "COURSE_IS_FREE", "“" + offer.title() + "” is free: just start learning.", details);
    }
    if (cart.heldCourses().contains(courseId) || offer.instructorId().equals(cart.buyerId())) {
      throw new RuleViolationException(
          "COURSE_ALREADY_OWNED", "You already have “" + offer.title() + "”.", details);
    }
    return new Quote.Line(offer.courseId(), offer.slug(), offer.title(), offer.price());
  }

  private static Money discount(
      Coupon coupon, List<Quote.Line> lines, Money subtotal, Instant now) {
    Set<UUID> courseIds = lines.stream().map(Quote.Line::courseId).collect(Collectors.toSet());
    CouponEligibility.require(new CouponEligibility.Context(coupon, courseIds, subtotal, now));
    // ⭐ the BASE: a course-scoped coupon discounts that course's line only
    Money base =
        coupon
            .courseId()
            .map(
                id ->
                    lines.stream()
                        .filter(l -> l.courseId().equals(id))
                        .findFirst()
                        .orElseThrow()
                        .price())
            .orElse(subtotal);
    return coupon.discount().discountOn(base);
  }

  private static Money zero(Money like) {
    return new Money(0, like.currency());
  }
}
