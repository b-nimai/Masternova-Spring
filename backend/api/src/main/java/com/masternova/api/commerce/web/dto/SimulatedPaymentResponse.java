package com.masternova.api.commerce.web.dto;

/** DEV ONLY: what the provider's checkout would hand the browser, ready for {@code confirm}. */
public record SimulatedPaymentResponse(
    String providerOrderId, String paymentId, String signature) {}
