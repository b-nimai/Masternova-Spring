package com.masternova.api.commerce.web;

import com.masternova.api.commerce.application.OrderQueries;
import com.masternova.api.commerce.domain.PaymentSimulator;
import com.masternova.api.commerce.web.dto.SimulatedPaymentResponse;
import com.masternova.api.identity.CurrentUser;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV ONLY: plays Razorpay Checkout's modal for the FAKE provider — returns what Razorpay's {@code
 * handler} would get after a successful payment, for the caller's OWN order. The SPA then confirms
 * as usual. ⭐ The bean doesn't exist unless {@code masternova.commerce.provider=fake}, which must
 * be set by name (the default is razorpay): with Razorpay the route is a 404.
 */
@RestController
@ConditionalOnProperty(name = "masternova.commerce.provider", havingValue = "fake")
class DevPaymentController {

  private final PaymentSimulator simulator;
  private final OrderQueries orders;

  DevPaymentController(PaymentSimulator simulator, OrderQueries orders) {
    this.simulator = simulator;
    this.orders = orders;
  }

  @PostMapping("/api/v1/dev/fake-payments/{orderId}")
  SimulatedPaymentResponse pay(CurrentUser me, @PathVariable UUID orderId) {
    String providerOrderId = orders.mine(me.id(), orderId).requireProviderOrderId();
    var paid = simulator.simulateCheckout(providerOrderId);
    return new SimulatedPaymentResponse(providerOrderId, paid.paymentId(), paid.signature());
  }
}
