package com.masternova.api.catalog.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Confirms the price in minor units of the catalog currency (INR paise); 0 = free. */
public record UpdatePricingRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull @PositiveOrZero @Max(100_000_00) Long priceMinor) {} // at most ₹1,00,000
