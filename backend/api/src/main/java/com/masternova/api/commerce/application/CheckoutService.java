package com.masternova.api.commerce.application;

import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.domain.OrderRepository;
import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.commerce.domain.PaymentGateway.CheckoutCallback;
import com.masternova.api.commerce.domain.PaymentProviderException;
import com.masternova.api.commerce.domain.Quote;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.ValidationException;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checkout's single entry point (LLD §5): the controller calls one method, this class runs the
 * sequence.
 *
 * <p>⭐ FACADE. Force: checkout is quote → order → provider → order again, with the provider call
 * BETWEEN two short transactions (never inside one: a slow provider would hold a connection and a
 * row lock for seconds, and its success can't be rolled back anyway). The sequence and the
 * transaction boundaries live here; the rules live in the pricer, the state machine and the
 * gateway.
 *
 * <p>Idempotency is the platform's: {@code POST /checkout} requires an {@code Idempotency-Key}, so
 * a retried request replays the first answer instead of opening a second order.
 */
@Service
@DesignPattern(
    value = Pattern.FACADE,
    role = "Facade: one entry point over pricing, the order, the provider and two transactions",
    note = "patterns/docs/14-facade.md")
public class CheckoutService {

  private final PricingService pricing;
  private final OrderRepository orders;
  private final PaymentGateway gateway;
  private final PaymentEvents payments;
  private final TransactionTemplate tx;
  private final Clock clock;

  CheckoutService(
      PricingService pricing,
      OrderRepository orders,
      PaymentGateway gateway,
      PaymentEvents payments,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.pricing = pricing;
    this.orders = orders;
    this.gateway = gateway;
    this.payments = payments;
    this.tx = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  /**
   * An order and what the browser needs to open the provider's checkout.
   *
   * @param publicKey the provider's public key; empty for a free order (nothing to pay)
   */
  public record Checkout(Order order, Optional<String> publicKey) {}

  public Checkout checkout(UUID buyerId, List<UUID> courseIds, Optional<String> couponCode) {
    Quote quote = pricing.quote(buyerId, courseIds, couponCode); // ⭐ re-priced: never trusted
    UUID orderId = UUID.randomUUID();

    if (quote.isFree()) { // a 100% coupon: paid and granted in ONE transaction, no provider
      Order order = Order.place(orderId, buyerId, quote, Order.NO_PROVIDER, clock.instant());
      return new Checkout(payments.placePaid(order).order(), Optional.empty());
    }

    // tx 1: the order exists before the provider hears of it — a webhook can always find it
    Order placed =
        tx.execute(
            s ->
                orders.save(
                    Order.place(orderId, buyerId, quote, gateway.provider(), clock.instant())));
    try {
      String providerOrderId = gateway.createOrder(orderId, quote.total()); // no transaction open
      // tx 2: now the browser may pay it
      Order ready =
          tx.execute(
              s -> {
                Order o = orders.lockById(placed.id()).orElseThrow();
                o.attachProviderOrder(providerOrderId);
                return o;
              });
      return new Checkout(ready, Optional.of(gateway.publicKey()));
    } catch (RuntimeException e) {
      // ⭐ ANY failure after tx 1 ends the attempt — a provider outage, an unreadable answer, a
      //    failed tx 2 — or the order would sit PENDING forever with nothing to pay against
      payments.apply(
          orderId,
          new PaymentEvent.PaymentFailed(
              Optional.empty(),
              e instanceof PaymentProviderException
                  ? "provider: " + e.getMessage()
                  : "checkout: " + e.getClass().getSimpleName()));
      throw e;
    }
  }

  /**
   * The browser's callback after paying (the redirect). Races the webhook; whichever comes second
   * is ignored by the state machine, and both answer with the order as it now is.
   */
  public Order confirm(UUID buyerId, UUID orderId, String paymentId, String signature) {
    Order order =
        orders
            .findByIdAndBuyerId(orderId, buyerId)
            .orElseThrow(() -> new NotFoundException("Order", orderId));
    CheckoutCallback callback =
        new CheckoutCallback(order.requireProviderOrderId(), paymentId, signature);
    if (!gateway.isAuthentic(callback)) {
      throw ValidationException.of(
          "signature", "PAYMENT_SIGNATURE_INVALID", "The payment confirmation isn't authentic.");
    }
    // the state machine answers a late confirm (the webhook was first) by ignoring it
    return payments.apply(orderId, new PaymentEvent.Captured(paymentId, Optional.empty())).order();
  }
}
