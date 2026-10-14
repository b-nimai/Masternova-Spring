package com.masternova.api.commerce.infrastructure;

import com.masternova.api.commerce.application.OrderQueries;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.ConflictException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV ONLY: plays Razorpay Checkout's modal for the FAKE provider — returns what Razorpay's {@code
 * handler} would get after a successful payment, for the caller's OWN order. The SPA then confirms
 * as usual. ⭐ The bean doesn't exist unless {@code masternova.commerce.provider=fake}: with
 * Razorpay configured the route is a 404.
 */
@RestController
@ConditionalOnProperty(name = "masternova.commerce.provider", havingValue = "fake")
class FakeCheckoutController {

  /** Razorpay's handler argument, by its field names. */
  record SimulatedPaymentResponse(String providerOrderId, String paymentId, String signature) {}

  private final FakePaymentGateway fake;
  private final OrderQueries orders;

  FakeCheckoutController(FakePaymentGateway fake, OrderQueries orders) {
    this.fake = fake;
    this.orders = orders;
  }

  @PostMapping("/api/v1/dev/fake-payments/{orderId}")
  SimulatedPaymentResponse pay(CurrentUser me, @PathVariable UUID orderId) {
    String providerOrderId =
        orders
            .mine(me.id(), orderId)
            .providerOrderId()
            .orElseThrow(
                () ->
                    new ConflictException(
                        "ORDER_NOT_AWAITING_PAYMENT", "This order isn't waiting for a payment."));
    var paid = fake.simulateCheckout(providerOrderId);
    return new SimulatedPaymentResponse(providerOrderId, paid.paymentId(), paid.signature());
  }
}
