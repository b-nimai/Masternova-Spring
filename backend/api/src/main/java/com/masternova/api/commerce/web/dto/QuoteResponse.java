package com.masternova.api.commerce.web.dto;

import com.masternova.api.commerce.domain.Quote;
import java.util.List;
import java.util.UUID;

/** A priced cart (API conventions §5: minor units + one currency). */
public record QuoteResponse(
    List<Line> lines,
    long subtotalMinor,
    long discountMinor,
    long totalMinor,
    String currency,
    String couponCode) {

  public record Line(UUID courseId, String slug, String title, long priceMinor) {}

  public static QuoteResponse from(Quote q) {
    return new QuoteResponse(
        q.lines().stream()
            .map(l -> new Line(l.courseId(), l.slug(), l.title(), l.price().amountMinor()))
            .toList(),
        q.subtotal().amountMinor(),
        q.discount().amountMinor(),
        q.total().amountMinor(),
        q.total().currency().getCurrencyCode(),
        q.couponCode().orElse(null));
  }
}
