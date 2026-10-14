package com.masternova.api.commerce.domain;

/**
 * DEV ONLY: plays the provider's checkout modal, returning what its browser callback would carry.
 * Implemented by the FAKE gateway alone and present only while it runs.
 *
 * <p>⭐ An interface for LAYERING, not for a second implementation: the dev endpoint lives in {@code
 * web/}, and web code may depend on the domain, never on {@code infrastructure/}.
 */
public interface PaymentSimulator {

  /** What the provider's checkout hands the browser after a successful payment. */
  record SimulatedPayment(String paymentId, String signature) {}

  SimulatedPayment simulateCheckout(String providerOrderId);
}
