package com.masternova.api.commerce.domain;

import static com.masternova.api.commerce.domain.Coupons.inr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.commerce.domain.PaymentEvent.Captured;
import com.masternova.api.commerce.domain.PaymentEvent.PaymentFailed;
import com.masternova.api.commerce.domain.PaymentEvent.Refunded;
import com.masternova.api.commerce.domain.Transition.Ignore;
import com.masternova.api.commerce.domain.Transition.Move;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Context: what an applied event records, and what it refuses to record. */
class OrderTest {

  static final Instant LATER = Orders.PLACED.plusSeconds(60);

  @Test
  void aPlacedOrderIsTheQuoteFrozen() {
    UUID course = UUID.randomUUID();
    Quote quote =
        new Quote(
            List.of(new Quote.Line(course, "java", "Java", inr(49_900))),
            inr(49_900),
            inr(9_980),
            inr(39_920),
            Optional.of("SAVE20"));
    Order order =
        Order.place(UUID.randomUUID(), UUID.randomUUID(), quote, "RAZORPAY", Orders.PLACED);

    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.total()).isEqualTo(inr(39_920));
    assertThat(order.discount()).isEqualTo(inr(9_980));
    assertThat(order.couponCode()).contains("SAVE20");
    assertThat(order.lines()).containsExactly(new Order.Line(course, "java", "Java", 49_900));
    assertThat(order.paidAt()).isEmpty();
  }

  @Test
  void aCaptureRecordsThePayment() {
    Order order = Orders.pending();

    assertThat(order.apply(new Captured("pay_1", Optional.of(inr(49_900))), LATER))
        .isInstanceOf(Move.class);
    assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    assertThat(order.paidAt()).contains(LATER);
    assertThat(order.providerPaymentId()).contains("pay_1");
  }

  @Test
  void aCaptureOfTheWrongAmountIsNeverAPayment() {
    Order order = Orders.pending();

    Transition t = order.apply(new Captured("pay_1", Optional.of(inr(100))), LATER);

    assertThat(t).isInstanceOf(Ignore.class);
    assertThat(((Ignore) t).why()).contains("INR 1.00", "INR 499.00");
    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void aFailureIsRememberedAndARetryCanStillPay() {
    Order order = Orders.pending();
    order.apply(new PaymentFailed(Optional.of("pay_0"), "x".repeat(600)), LATER);

    assertThat(order.status()).isEqualTo(OrderStatus.FAILED);
    assertThat(order.lastPaymentError()).hasValueSatisfying(e -> assertThat(e).hasSize(500));

    order.apply(new Captured("pay_1", Optional.empty()), LATER);
    assertThat(order.status()).isEqualTo(OrderStatus.PAID);
  }

  @Test
  void aRefundRecordsTheRefundAndKeepsThePayment() {
    Order order = Orders.pending();
    order.apply(new Captured("pay_1", Optional.empty()), LATER);
    order.apply(new Refunded("rfnd_1"), LATER.plusSeconds(1));

    assertThat(order.status()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(order.refundedAt()).contains(LATER.plusSeconds(1));
    assertThat(order.providerRefundId()).contains("rfnd_1");
    assertThat(order.paidAt()).contains(LATER); // history kept
  }

  @Test
  void theProviderOrderIsAttachedOnceWhilePending() {
    Order order = Orders.pending();
    order.attachProviderOrder("order_1");
    assertThat(order.providerOrderId()).contains("order_1");

    assertThatThrownBy(() -> order.attachProviderOrder("order_2"))
        .isInstanceOf(IllegalStateException.class);
  }
}
