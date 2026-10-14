package com.masternova.api.commerce.domain;

import java.util.Objects;

/** What a {@link PaymentEvent} does to an order: move it (with an effect), or nothing. */
public sealed interface Transition {

  /** What the move obliges the application layer to do, in the same transaction (ADR-0019). */
  enum Effect {
    NONE,
    /** Give the buyer every course in the order. */
    GRANT,
    /** Take every course in the order back. */
    REVOKE
  }

  record Move(OrderStatus from, OrderStatus to, Effect effect) implements Transition {
    public Move {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
      Objects.requireNonNull(effect, "effect");
    }
  }

  /**
   * ⭐ Not an error: a duplicate, or a late message about a world that has moved on. The senders are
   * machines replaying history; the order's current state already answers them (LLD §3).
   */
  record Ignore(OrderStatus state, String why) implements Transition {
    public Ignore {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(why, "why");
    }
  }
}
