package com.masternova.patterns.strategy;

/**
 * <b>Strategy</b> — one interchangeable way to take a payment.
 *
 * <p>The checkout flow depends on this interface only. Adding a provider means adding a class,
 * not editing an {@code if/else} chain in checkout (Open/Closed Principle).
 */
public interface PaymentGateway {

  /** Which provider this strategy implements — used to register it. */
  PaymentProvider provider();

  PaymentResult charge(ChargeRequest request);
}
