package com.masternova.api.commerce.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * An order's status, with the TRANSITION TABLE as data (LLD §3): the whiteboard diagram, checked by
 * {@link Order#apply} on every move. The behaviour of each state lives in {@link OrderState}; this
 * table is what the behaviour is not allowed to violate.
 *
 * <p>⭐ Forward-only: every legal move goes to a higher {@link #rank()}, so no sequence of messages,
 * however late or reordered, can take an order back.
 */
public enum OrderStatus {
  PENDING(0),
  FAILED(1),
  PAID(2),
  REFUNDED(3);

  private static final Map<OrderStatus, Set<OrderStatus>> SUCCESSORS =
      new EnumMap<>(OrderStatus.class);

  static {
    // ⭐ EnumSet: a bit vector, the cheapest Set there is, and iteration in declaration order
    SUCCESSORS.put(PENDING, EnumSet.of(FAILED, PAID, REFUNDED));
    SUCCESSORS.put(FAILED, EnumSet.of(PAID, REFUNDED)); // a retried attempt may still succeed
    SUCCESSORS.put(PAID, EnumSet.of(REFUNDED));
    SUCCESSORS.put(REFUNDED, EnumSet.noneOf(OrderStatus.class)); // terminal
  }

  private final int rank;

  OrderStatus(int rank) {
    this.rank = rank;
  }

  public int rank() {
    return rank;
  }

  public Set<OrderStatus> successors() {
    return Collections.unmodifiableSet(SUCCESSORS.get(this));
  }

  public boolean canMoveTo(OrderStatus next) {
    return SUCCESSORS.get(this).contains(next);
  }
}
