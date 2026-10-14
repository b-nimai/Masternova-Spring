package com.masternova.api.commerce.domain;

import com.masternova.api.commerce.domain.PaymentEvent.Captured;
import com.masternova.api.commerce.domain.PaymentEvent.PaymentFailed;
import com.masternova.api.commerce.domain.Transition.Ignore;
import com.masternova.api.commerce.domain.Transition.Move;
import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * A purchase: the AGGREGATE ROOT of commerce (LLD §3). Its lines and totals are fixed when it is
 * placed; afterwards only {@link #apply} changes it, and only along {@link OrderStatus}'s table.
 *
 * <p>The Context of the State pattern: {@link #apply} asks {@link #state()} what an event means,
 * checks the answer against the table, then records it. The application layer performs the {@link
 * Transition.Effect} (grant, revoke) in the same transaction (ADR-0019).
 *
 * <p>JPA entity name {@code PurchaseOrder}: {@code order} is a keyword in JPQL as in SQL.
 */
@Entity(name = "PurchaseOrder")
@Table(name = "purchase_order")
@DesignPattern(value = Pattern.STATE, role = "Context", note = "patterns/docs/02-state.md")
public class Order {

  /** A course as sold: a snapshot, so a later price or title change never rewrites an order. */
  @Embeddable
  public record Line(
      @Column(name = "course_id") UUID courseId,
      @Column(name = "slug") String slug,
      @Column(name = "title") String title,
      @Column(name = "price_minor") long priceMinor) {}

  /** {@code NONE}: a free order (a 100% coupon) — paid without a provider. */
  public static final String NO_PROVIDER = "NONE";

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID buyerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private OrderStatus status;

  @Column(nullable = false, length = 3)
  private Currency currency;

  @Column(name = "subtotal_minor", nullable = false)
  private long subtotalMinor;

  @Column(name = "discount_minor", nullable = false)
  private long discountMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  @Column(name = "coupon_code", length = 40)
  private String couponCode;

  @Column(nullable = false, length = 20)
  private String provider;

  @Column(name = "provider_order_id", length = 100)
  private String providerOrderId;

  @Column(name = "provider_payment_id", length = 100)
  private String providerPaymentId;

  @Column(name = "provider_refund_id", length = 100)
  private String providerRefundId;

  @Column(name = "last_payment_error", length = 500)
  private String lastPaymentError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  // ⭐ EAGER on purpose: the lines are part of the order's value (≤ 20, never edited) and every
  //    reader needs them. @BatchSize loads the lines of a whole history page in one more statement.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_line", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "position")
  @BatchSize(size = 50)
  private List<Line> lines = new ArrayList<>();

  @Version private Long version;

  protected Order() {} // JPA

  /** A new, unpaid order for exactly what the quote says. */
  public static Order place(UUID id, UUID buyerId, Quote quote, String provider, Instant now) {
    Order o = new Order();
    o.id = Objects.requireNonNull(id, "id");
    o.buyerId = Objects.requireNonNull(buyerId, "buyerId");
    o.status = OrderStatus.PENDING;
    o.currency = quote.total().currency();
    o.subtotalMinor = quote.subtotal().amountMinor();
    o.discountMinor = quote.discount().amountMinor();
    o.totalMinor = quote.total().amountMinor();
    o.couponCode = quote.couponCode().orElse(null);
    o.provider = Objects.requireNonNull(provider, "provider");
    o.createdAt = now;
    quote
        .lines()
        .forEach(
            l -> o.lines.add(new Line(l.courseId(), l.slug(), l.title(), l.price().amountMinor())));
    return o;
  }

  /** What the provider calls this order. Set once, while it is still waiting for the money. */
  public void attachProviderOrder(String providerOrderId) {
    Objects.requireNonNull(providerOrderId, "providerOrderId");
    if (status != OrderStatus.PENDING || this.providerOrderId != null) {
      throw new IllegalStateException("order " + id + " already has a provider order");
    }
    this.providerOrderId = providerOrderId;
  }

  /** ⭐ The EFFECTIVE state: what each event means right now. */
  public OrderState state() {
    return OrderState.of(status);
  }

  /**
   * Applies what a messenger says happened. Returns what it meant; a {@link Move}'s effect is the
   * caller's to perform, in this transaction.
   */
  public Transition apply(PaymentEvent event, Instant now) {
    if (event instanceof Captured(var paymentId, var amount)
        && amount.isPresent()
        && !amount.get().equals(total())) {
      // ⭐ money we didn't ask for: never grant on it. The order stays unpaid; a human looks.
      return new Ignore(
          status, "captured " + amount.get().display() + ", expected " + total().display(), true);
    }
    Transition transition = state().on(event);
    if (transition instanceof Move(var from, var to, var effect)) {
      if (!from.canMoveTo(to)) { // the table guards the states: a drifted state fails loudly
        throw new IllegalStateException(from + " → " + to + " is not in the transition table");
      }
      status = to;
      switch (event) {
        case Captured c -> {
          paidAt = now;
          providerPaymentId = c.paymentId();
        }
        case PaymentFailed f -> lastPaymentError = truncate(f.reason());
        case PaymentEvent.Refunded r -> {
          refundedAt = now;
          providerRefundId = r.refundId();
        }
      }
    }
    return transition;
  }

  private static String truncate(String reason) {
    return reason.length() <= 500 ? reason : reason.substring(0, 500);
  }

  public Money subtotal() {
    return new Money(subtotalMinor, currency);
  }

  public Money discount() {
    return new Money(discountMinor, currency);
  }

  public Money total() {
    return new Money(totalMinor, currency);
  }

  public UUID id() {
    return id;
  }

  public UUID buyerId() {
    return buyerId;
  }

  public OrderStatus status() {
    return status;
  }

  public List<Line> lines() {
    return List.copyOf(lines);
  }

  public List<UUID> courseIds() {
    return lines.stream().map(Line::courseId).toList();
  }

  public Optional<String> couponCode() {
    return Optional.ofNullable(couponCode);
  }

  public String provider() {
    return provider;
  }

  public Optional<String> providerOrderId() {
    return Optional.ofNullable(providerOrderId);
  }

  public Optional<String> providerPaymentId() {
    return Optional.ofNullable(providerPaymentId);
  }

  public Optional<String> providerRefundId() {
    return Optional.ofNullable(providerRefundId);
  }

  public Optional<String> lastPaymentError() {
    return Optional.ofNullable(lastPaymentError);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> paidAt() {
    return Optional.ofNullable(paidAt);
  }

  public Optional<Instant> refundedAt() {
    return Optional.ofNullable(refundedAt);
  }
}
