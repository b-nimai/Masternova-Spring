package com.masternova.api.commerce.web.dto;

import java.util.UUID;

/** One course in a quote or an order, as priced. */
public record OrderLineResponse(UUID courseId, String slug, String title, long priceMinor) {}
