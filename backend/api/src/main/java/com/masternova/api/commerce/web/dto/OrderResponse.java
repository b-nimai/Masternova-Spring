package com.masternova.api.commerce.web.dto;

import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as its buyer sees it (API conventions §5: minor units + currency). */
public record OrderResponse(
    UUID id,
    OrderStatus status,
    List<OrderLineResponse> lines,
    long subtotalMinor,
    long discountMinor,
    long totalMinor,
    String currency,
    String couponCode,
    Instant createdAt,
    Instant paidAt,
    Instant refundedAt) {

  public static OrderResponse from(Order o) {
    return new OrderResponse(
        o.id(),
        o.status(),
        o.lines().stream()
            .map(l -> new OrderLineResponse(l.courseId(), l.slug(), l.title(), l.priceMinor()))
            .toList(),
        o.subtotal().amountMinor(),
        o.discount().amountMinor(),
        o.total().amountMinor(),
        o.total().currency().getCurrencyCode(),
        o.couponCode().orElse(null),
        o.createdAt(),
        o.paidAt().orElse(null),
        o.refundedAt().orElse(null));
  }
}
