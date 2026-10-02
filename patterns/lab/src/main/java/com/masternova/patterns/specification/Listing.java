package com.masternova.patterns.specification;

import java.math.BigDecimal;
import java.util.UUID;

/** A catalog row, simplified: the candidate the rules are evaluated against. */
public record Listing(
    String title, boolean published, long priceMinor, BigDecimal rating, UUID instructorId) {}
