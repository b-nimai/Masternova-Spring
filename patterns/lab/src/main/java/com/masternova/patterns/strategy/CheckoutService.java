package com.masternova.patterns.strategy;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * <b>Context</b> — uses a strategy without knowing which concrete class it is.
 *
 * <p>The strategies arrive as a collection, exactly like Spring injects {@code
 * List<PaymentGateway>} with every bean that implements the interface. They are indexed once into
 * an {@link EnumMap} registry, so picking one is a lookup — no {@code switch} on the provider.
 */
public final class CheckoutService {

  private final Map<PaymentProvider, PaymentGateway> gateways =
      new EnumMap<>(PaymentProvider.class);

  public CheckoutService(Collection<? extends PaymentGateway> strategies) {
    for (PaymentGateway gateway : strategies) {
      PaymentGateway previous = gateways.putIfAbsent(gateway.provider(), gateway);
      if (previous != null) {
        throw new IllegalArgumentException("two gateways registered for " + gateway.provider());
      }
    }
  }

  /** Charges via the chosen provider and turns the outcome into a user-facing message. */
  public String pay(PaymentProvider provider, ChargeRequest request) {
    PaymentGateway gateway = gateways.get(provider);
    if (gateway == null) {
      throw new IllegalArgumentException("no gateway for " + provider);
    }

    // Pattern-matching switch over a sealed type: exhaustive, no default branch needed.
    return switch (gateway.charge(request)) {
      case PaymentResult.Approved(String ref) -> "Paid — reference " + ref;
      case PaymentResult.Declined(String reason) -> "Payment declined: " + reason;
    };
  }
}
