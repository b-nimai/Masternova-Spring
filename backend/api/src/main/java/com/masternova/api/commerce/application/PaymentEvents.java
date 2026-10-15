package com.masternova.api.commerce.application;

import com.masternova.api.commerce.OrderItem;
import com.masternova.api.commerce.OrderPaid;
import com.masternova.api.commerce.OrderRefunded;
import com.masternova.api.commerce.domain.CouponRepository;
import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.domain.OrderRepository;
import com.masternova.api.commerce.domain.OrderStatus;
import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.Transition;
import com.masternova.api.commerce.domain.Transition.Effect;
import com.masternova.api.commerce.domain.Transition.Ignore;
import com.masternova.api.commerce.domain.Transition.Move;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.identity.Contact;
import com.masternova.api.identity.IdentityApi;
import com.masternova.api.platform.EventPublisher;
import com.masternova.api.platform.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONE place a payment message changes an order (LLD §5): every messenger — the browser's
 * confirm, a webhook, our own refund call, a free checkout — comes through here.
 *
 * <p>⭐ One transaction: lock the order row, let the state machine decide, perform the move's effect
 * (grant or revoke every course, through entitlement's API, which JOINS this transaction), count
 * the coupon, publish the event to the outbox. All of it commits or none of it does (ADR-0019). The
 * row lock serialises racing messengers: the loser reads the winner's state and is ignored.
 */
@Service
public class PaymentEvents {

  private static final Logger log = LoggerFactory.getLogger(PaymentEvents.class);

  /** Why the courses went: the revoke's reason, shown to support. */
  static final String REFUND_REASON = "refund";

  private final OrderRepository orders;
  private final CouponRepository coupons;
  private final EntitlementApi entitlements;
  private final IdentityApi identity;
  private final EventPublisher events;
  private final MeterRegistry meters;
  private final Clock clock;

  PaymentEvents(
      OrderRepository orders,
      CouponRepository coupons,
      EntitlementApi entitlements,
      IdentityApi identity,
      EventPublisher events,
      MeterRegistry meters,
      Clock clock) {
    this.orders = orders;
    this.coupons = coupons;
    this.entitlements = entitlements;
    this.identity = identity;
    this.events = events;
    this.meters = meters;
    this.clock = clock;
  }

  /** What happened, and the order as it is now. */
  public record Applied(Order order, Transition transition) {}

  @Transactional
  public Applied apply(UUID orderId, PaymentEvent event) {
    Order order =
        orders.lockById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    return applyTo(order, event);
  }

  /** For webhooks, which know the provider's order id; empty if the order isn't ours. */
  @Transactional
  public Optional<Applied> applyByProviderOrder(String providerOrderId, PaymentEvent event) {
    return orders.lockByProviderOrderId(providerOrderId).map(order -> applyTo(order, event));
  }

  /**
   * A new order that is paid as it is placed (a 100% coupon): no provider, no lock needed — nobody
   * else can see it yet.
   */
  @Transactional
  public Applied placePaid(Order order) {
    orders.save(order);
    return applyTo(
        order, new PaymentEvent.Captured("free_" + order.id(), Optional.of(order.total())));
  }

  private Applied applyTo(Order order, PaymentEvent event) {
    Instant now = clock.instant();
    Transition transition = order.apply(event, now);
    switch (transition) {
      case Move m -> {
        perform(order, m, now);
        log.info("Order {} {} → {} on {}", order.id(), m.from(), m.to(), name(event));
      }
      case Ignore i when i.anomaly() ->
          log.error("Order {} IGNORED {}: {} — needs a human", order.id(), name(event), i.why());
      case Ignore i -> log.info("Order {} ignored {}: {}", order.id(), name(event), i.why());
    }
    meters
        .counter("commerce.payment.events", "event", name(event), "outcome", outcome(transition))
        .increment();
    return new Applied(order, transition);
  }

  private void perform(Order order, Move move, Instant now) {
    // ⭐ every effect joins this transaction: the order is PAID iff every course is granted, and
    //    REFUNDED-from-PAID iff every course is revoked (ADR-0019)
    UUID buyer = order.buyerId();
    switch (move.effect()) {
      case GRANT -> {
        order
            .courseIds()
            .forEach(
                c -> entitlements.grant(buyer, c, EntitlementSource.PURCHASE, Optional.empty()));
        order.couponCode().ifPresent(coupons::redeem);
      }
      case REVOKE -> {
        // ⭐ a course the buyer also paid for in another order stays theirs (one query)
        Set<UUID> keep =
            orders.paidElsewhere(buyer, order.id(), OrderStatus.PAID, order.courseIds());
        order.courseIds().stream()
            .filter(c -> !keep.contains(c))
            .forEach(c -> entitlements.revoke(buyer, c, REFUND_REASON));
      }
      case NONE -> {}
    }
    if (move.to() == OrderStatus.PAID || move.to() == OrderStatus.REFUNDED) {
      Contact contact =
          identity
              .contact(buyer)
              .orElseThrow(() -> new IllegalStateException("buyer " + buyer + " is gone"));
      List<OrderItem> items =
          order.lines().stream()
              .map(l -> new OrderItem(l.courseId().toString(), l.slug(), l.title(), l.priceMinor()))
              .toList();
      String orderId = order.id().toString();
      long total = order.total().amountMinor();
      String currency = order.total().currency().getCurrencyCode();
      events.publish(
          move.to() == OrderStatus.PAID
              ? new OrderPaid(
                  orderId,
                  buyer.toString(),
                  contact.email(),
                  contact.displayName(),
                  items,
                  total,
                  currency,
                  now)
              : new OrderRefunded(
                  orderId,
                  buyer.toString(),
                  contact.email(),
                  contact.displayName(),
                  items,
                  total,
                  currency,
                  move.effect() == Effect.REVOKE,
                  now));
    }
  }

  private static String name(PaymentEvent event) {
    return switch (event) {
      case PaymentEvent.Captured c -> "captured";
      case PaymentEvent.PaymentFailed f -> "failed";
      case PaymentEvent.Refunded r -> "refunded";
    };
  }

  private static String outcome(Transition t) {
    return switch (t) {
      case Move m -> "moved";
      case Ignore i when i.anomaly() -> "anomaly";
      case Ignore i -> "ignored";
    };
  }
}
