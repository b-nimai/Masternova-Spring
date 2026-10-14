package com.masternova.api.commerce.application;

import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.domain.OrderRepository;
import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.platform.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An admin refunds a paid order (LLD §5): CLAIM the order for the refund ({@code
 * Order.requestRefund} under its row lock, so a concurrent second request is refused before it
 * reaches the provider), let the provider give the money back (no transaction open), then apply the
 * refund through {@link PaymentEvents} like any other payment message — which revokes the courses
 * and publishes {@code OrderRefunded}, in one transaction. The provider's later {@code
 * refund.processed} webhook finds the order REFUNDED and is ignored.
 */
@Service
public class RefundService {

  /** A claim older than this belongs to a request that crashed mid-refund. */
  static final Duration STALE_CLAIM = Duration.ofMinutes(5);

  private final OrderRepository orders;
  private final PaymentGateway gateway;
  private final PaymentEvents payments;
  private final TransactionTemplate tx;
  private final Clock clock;

  RefundService(
      OrderRepository orders,
      PaymentGateway gateway,
      PaymentEvents payments,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.orders = orders;
    this.gateway = gateway;
    this.payments = payments;
    this.tx = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  public Order refund(UUID orderId) {
    Instant now = clock.instant();
    // ⭐ the claim, under the order's row lock: the aggregate decides (PAID? already refunding?)
    Order order =
        tx.execute(
            s -> {
              Order o =
                  orders
                      .lockById(orderId)
                      .orElseThrow(() -> new NotFoundException("Order", orderId));
              o.requestRefund(now, now.minus(STALE_CLAIM));
              return o;
            });
    String refundId;
    try {
      // a 100% coupon: no money moved, so none goes back
      refundId =
          order.total().isZero()
              ? "free_" + orderId
              : gateway.refund(order.providerPaymentId().orElseThrow(), order.total(), orderId);
    } catch (RuntimeException e) {
      tx.executeWithoutResult(s -> orders.lockById(orderId).orElseThrow().releaseRefund());
      throw e;
    }
    return payments.apply(orderId, new PaymentEvent.Refunded(refundId)).order();
  }
}
