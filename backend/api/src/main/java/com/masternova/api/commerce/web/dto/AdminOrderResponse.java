package com.masternova.api.commerce.web.dto;

import com.masternova.api.commerce.domain.Order;
import java.util.UUID;

/** An order as support sees it: the buyer's order plus who bought it and the provider's ids. */
public record AdminOrderResponse(
    OrderResponse order,
    UUID buyerId,
    String provider,
    String providerOrderId,
    String providerPaymentId,
    String providerRefundId,
    String lastPaymentError) {

  public static AdminOrderResponse from(Order o) {
    return new AdminOrderResponse(
        OrderResponse.from(o),
        o.buyerId(),
        o.provider(),
        o.providerOrderId().orElse(null),
        o.providerPaymentId().orElse(null),
        o.providerRefundId().orElse(null),
        o.lastPaymentError().orElse(null));
  }
}
