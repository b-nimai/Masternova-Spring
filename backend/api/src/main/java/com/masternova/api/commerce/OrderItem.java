package com.masternova.api.commerce;

/**
 * One course in a purchase event, as sold — shared by {@link OrderPaid} and {@link OrderRefunded}.
 */
public record OrderItem(String courseId, String slug, String title, long priceMinor) {}
