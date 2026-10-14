package com.masternova.api.commerce.domain;

import com.masternova.kernel.money.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A priced cart (LLD §3, Pricing): what checkout would charge right now. A value: recomputed on
 * every request, never stored — an order keeps its own copy of the lines.
 *
 * @param couponCode the applied coupon, normalized; empty if none was given
 */
public record Quote(
    List<Line> lines, Money subtotal, Money discount, Money total, Optional<String> couponCode) {

  /** One course as sold: its title and price at quoting time. */
  public record Line(UUID courseId, String slug, String title, Money price) {
    public Line {
      Objects.requireNonNull(courseId, "courseId");
      Objects.requireNonNull(slug, "slug");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(price, "price");
    }
  }

  public Quote {
    lines = List.copyOf(lines);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("a quote has at least one line");
    }
    Objects.requireNonNull(couponCode, "couponCode");
    // ⭐ the invariant every later step relies on, checked where the numbers are made
    if (!subtotal.minus(discount).equals(total)) {
      throw new IllegalArgumentException("total must be subtotal − discount");
    }
  }

  /** Nothing to charge (a 100% coupon): checkout pays the order without a provider. */
  public boolean isFree() {
    return total.isZero();
  }
}
