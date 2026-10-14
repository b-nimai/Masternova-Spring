package com.masternova.api.commerce.application;

import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.domain.OrderRepository;
import com.masternova.api.commerce.domain.OrderStatus;
import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * An admin refunds a paid order (LLD §5): the provider gives the money back (no transaction open),
 * then the refund goes through {@link PaymentEvents} like any other payment message — which revokes
 * every course and publishes {@code OrderRefunded}, in one transaction. The provider's later {@code
 * refund.processed} webhook finds the order REFUNDED and is ignored.
 */
@Service
public class RefundService {

  private final OrderRepository orders;
  private final PaymentGateway gateway;
  private final PaymentEvents payments;

  RefundService(OrderRepository orders, PaymentGateway gateway, PaymentEvents payments) {
    this.orders = orders;
    this.gateway = gateway;
    this.payments = payments;
  }

  public Order refund(UUID orderId) {
    Order order =
        orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    if (order.status() != OrderStatus.PAID) {
      throw new ConflictException(
          "ORDER_NOT_REFUNDABLE",
          "Only a paid order can be refunded.",
          Map.of("status", order.status().name()));
    }
    if (order.total().isZero()) { // a 100% coupon: no money moved, so none goes back
      return payments.apply(orderId, new PaymentEvent.Refunded("free_" + orderId)).order();
    }
    String refundId =
        gateway.refund(order.providerPaymentId().orElseThrow(), order.total(), orderId);
    return payments.apply(orderId, new PaymentEvent.Refunded(refundId)).order();
  }
}
