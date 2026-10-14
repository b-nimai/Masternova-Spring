package com.masternova.api.commerce.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A cart to price, or to check out. ⭐ Course ids and a code — never an amount: the server prices
 * (LLD §3). The size limit is configuration, so the service checks it ({@code CART_TOO_LARGE}).
 *
 * @param couponCode optional, case-insensitive
 */
public record QuoteRequest(
    @NotNull List<@NotNull UUID> courseIds, @Size(max = 40) String couponCode) {}
