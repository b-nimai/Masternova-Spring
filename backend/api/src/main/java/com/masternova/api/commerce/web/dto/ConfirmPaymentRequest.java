package com.masternova.api.commerce.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the provider's checkout handed the browser (Razorpay's {@code razorpay_payment_id}, …). */
public record ConfirmPaymentRequest(
    @NotBlank @Size(max = 100) String paymentId, @NotBlank @Size(max = 200) String signature) {}
