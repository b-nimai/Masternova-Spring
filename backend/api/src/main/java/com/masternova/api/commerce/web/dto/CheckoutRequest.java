package com.masternova.api.commerce.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Same shape as a quote: course ids and an optional code, never an amount (LLD §3). */
public record CheckoutRequest(
    @NotNull List<@NotNull UUID> courseIds, @Size(max = 40) String couponCode) {}
