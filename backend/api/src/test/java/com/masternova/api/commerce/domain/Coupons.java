package com.masternova.api.commerce.domain;

import com.masternova.kernel.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Test coupons in INR, built through the real factory. */
public final class Coupons {

  public static final Currency INR = Currency.getInstance("INR");
  static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

  private Coupons() {}

  public static Money inr(long minor) {
    return new Money(minor, INR);
  }

  public static Coupon.Terms percent(int percent) {
    return new Coupon.Terms(
        Optional.of(percent),
        Optional.empty(),
        Optional.empty(),
        inr(0),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public static Coupon.Terms amount(long minor) {
    return new Coupon.Terms(
        Optional.empty(),
        Optional.of(inr(minor)),
        Optional.empty(),
        inr(0),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public static Coupon coupon(Coupon.Terms terms) {
    return Coupon.create("SAVE", terms, INR, NOW.minusSeconds(3600));
  }

  public static Coupon.Terms capped(Coupon.Terms t, long capMinor) {
    return new Coupon.Terms(
        t.percentOff(),
        t.amountOff(),
        Optional.of(inr(capMinor)),
        t.minSubtotal(),
        t.courseId(),
        t.startsAt(),
        t.endsAt(),
        t.maxRedemptions());
  }

  public static Coupon.Terms minimum(Coupon.Terms t, long minSubtotalMinor) {
    return new Coupon.Terms(
        t.percentOff(),
        t.amountOff(),
        t.maxDiscount(),
        inr(minSubtotalMinor),
        t.courseId(),
        t.startsAt(),
        t.endsAt(),
        t.maxRedemptions());
  }

  public static Coupon.Terms forCourse(Coupon.Terms t, UUID courseId) {
    return new Coupon.Terms(
        t.percentOff(),
        t.amountOff(),
        t.maxDiscount(),
        t.minSubtotal(),
        Optional.of(courseId),
        t.startsAt(),
        t.endsAt(),
        t.maxRedemptions());
  }

  public static Coupon.Terms window(Coupon.Terms t, Instant startsAt, Instant endsAt) {
    return new Coupon.Terms(
        t.percentOff(),
        t.amountOff(),
        t.maxDiscount(),
        t.minSubtotal(),
        t.courseId(),
        Optional.ofNullable(startsAt),
        Optional.ofNullable(endsAt),
        t.maxRedemptions());
  }

  public static Coupon.Terms limit(Coupon.Terms t, int maxRedemptions) {
    return new Coupon.Terms(
        t.percentOff(),
        t.amountOff(),
        t.maxDiscount(),
        t.minSubtotal(),
        t.courseId(),
        t.startsAt(),
        t.endsAt(),
        Optional.of(maxRedemptions));
  }

  /** The database counts redemptions (never the entity); a unit test plays the database. */
  public static Coupon redeemed(Coupon coupon, int times) {
    ReflectionTestUtils.setField(coupon, "redemptions", times);
    return coupon;
  }
}
