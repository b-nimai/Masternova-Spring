package com.masternova.api.commerce.domain;

import static com.masternova.api.commerce.domain.Coupons.inr;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test orders built through the real factory. */
public final class Orders {

  public static final Instant PLACED = Instant.parse("2026-10-14T10:00:00Z");

  private Orders() {}

  /** One ₹499 course, no coupon. */
  public static Quote quote(UUID courseId) {
    return new Quote(
        List.of(new Quote.Line(courseId, "java", "Java", inr(49_900))),
        inr(49_900),
        inr(0),
        inr(49_900),
        Optional.empty());
  }

  public static Order pending() {
    return Order.place(
        UUID.randomUUID(), UUID.randomUUID(), quote(UUID.randomUUID()), "FAKE", PLACED);
  }
}
