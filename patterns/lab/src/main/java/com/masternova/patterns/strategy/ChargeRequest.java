package com.masternova.patterns.strategy;

import java.util.Objects;

/**
 * Immutable input shared by every strategy. Money is in minor units (paise / cents), never a
 * double.
 *
 * <p>Java: a {@code record} gives constructor, accessors, equals/hashCode/toString for free; the
 * compact constructor is where invariants live.
 */
public record ChargeRequest(String orderId, long amountMinor, String currency) {

  public ChargeRequest {
    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(currency, "currency");
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amountMinor must be positive, was " + amountMinor);
    }
  }
}
