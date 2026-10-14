package com.masternova.api.commerce.web.dto;

import com.masternova.api.commerce.application.CheckoutService.Checkout;

/**
 * A placed order and what the browser needs to open the provider's checkout widget. For a free
 * order (100% coupon) {@code payment} is null: it is already PAID.
 */
public record CheckoutResponse(OrderResponse order, Payment payment) {

  /**
   * @param provider {@code RAZORPAY} opens Razorpay Checkout; {@code FAKE} the dev simulator
   * @param publicKey Razorpay's {@code key} option (public by design)
   */
  public record Payment(
      String provider,
      String publicKey,
      String providerOrderId,
      long amountMinor,
      String currency) {}

  public static CheckoutResponse from(Checkout c) {
    var o = c.order();
    return new CheckoutResponse(
        OrderResponse.from(o),
        c.publicKey()
            .map(
                key ->
                    new Payment(
                        o.provider(),
                        key,
                        o.providerOrderId().orElseThrow(),
                        o.total().amountMinor(),
                        o.total().currency().getCurrencyCode()))
            .orElse(null));
  }
}
