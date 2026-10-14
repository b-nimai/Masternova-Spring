package com.masternova.api.commerce.domain;

import com.masternova.api.commerce.domain.PaymentEvent.Captured;
import com.masternova.api.commerce.domain.PaymentEvent.PaymentFailed;
import com.masternova.api.commerce.domain.Transition.Effect;
import com.masternova.api.commerce.domain.Transition.Ignore;
import com.masternova.api.commerce.domain.Transition.Move;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;

/**
 * An order's lifecycle as the STATE pattern (LLD §3): each state answers every {@link PaymentEvent}
 * with a {@link Move} or an {@link Ignore}. The twelve answers ARE the table in the LLD.
 *
 * <p>⭐ Force: messengers arrive late, twice and out of order, and what an event DOES depends on
 * where the order is — a refund revokes access from {@code PAID} and from nowhere else (nothing was
 * granted). A transition table alone can say WHERE an order may go, not WHAT the move must do; so
 * the table ({@link OrderStatus#successors()}) guards the states, and the states carry the effects.
 *
 * <p>Same family as {@code CourseState} and {@code UploadState}; the difference is that no event is
 * ever refused here — a payment message is history, not a request (no 409s to webhooks).
 */
@DesignPattern(value = Pattern.STATE, role = "State (sealed)", note = "patterns/docs/02-state.md")
public sealed interface OrderState {

  OrderStatus status();

  Transition on(PaymentEvent event);

  // ------------------------------------------------------------------ the states

  record Pending() implements OrderState {
    @Override
    public OrderStatus status() {
      return OrderStatus.PENDING;
    }

    @Override
    public Transition on(PaymentEvent event) {
      return switch (event) {
        case Captured c -> new Move(status(), OrderStatus.PAID, Effect.GRANT);
        case PaymentFailed f -> new Move(status(), OrderStatus.FAILED, Effect.NONE);
        // ⭐ the refund's webhook overtook the capture's: refunded, and NOTHING to revoke
        case PaymentEvent.Refunded r -> new Move(status(), OrderStatus.REFUNDED, Effect.NONE);
      };
    }
  }

  record Failed() implements OrderState {
    @Override
    public OrderStatus status() {
      return OrderStatus.FAILED;
    }

    @Override
    public Transition on(PaymentEvent event) {
      return switch (event) {
        case Captured c -> new Move(status(), OrderStatus.PAID, Effect.GRANT); // a retry worked
        case PaymentFailed f -> new Ignore(status(), "another attempt failed");
        case PaymentEvent.Refunded r -> new Move(status(), OrderStatus.REFUNDED, Effect.NONE);
      };
    }
  }

  record Paid() implements OrderState {
    @Override
    public OrderStatus status() {
      return OrderStatus.PAID;
    }

    @Override
    public Transition on(PaymentEvent event) {
      return switch (event) {
        case Captured c -> new Ignore(status(), "already paid");
        case PaymentFailed f -> new Ignore(status(), "an earlier attempt's failure, reported late");
        case PaymentEvent.Refunded r -> new Move(status(), OrderStatus.REFUNDED, Effect.REVOKE);
      };
    }
  }

  record Refunded() implements OrderState {
    @Override
    public OrderStatus status() {
      return OrderStatus.REFUNDED;
    }

    @Override
    public Transition on(PaymentEvent event) {
      // ⭐ terminal: a late capture after a refund must never grant access again
      return new Ignore(status(), "already refunded");
    }
  }

  static OrderState of(OrderStatus status) {
    return switch (status) {
      case PENDING -> new Pending();
      case FAILED -> new Failed();
      case PAID -> new Paid();
      case REFUNDED -> new Refunded();
    };
  }
}
