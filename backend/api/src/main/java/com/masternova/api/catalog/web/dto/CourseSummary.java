package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseLevel;
import com.masternova.api.catalog.domain.CourseStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A course card in a list. ⭐ Money as minor units + currency (API conventions §5): {@code
 * "priceMinor": 149900, "currency": "INR"} — the client formats it.
 */
public record CourseSummary(
    String id,
    String slug,
    String title,
    String subtitle,
    CourseLevel level,
    String language,
    CourseStatus status,
    long priceMinor,
    String currency,
    BigDecimal ratingAverage,
    int ratingCount,
    int lectureCount,
    int totalDurationSeconds,
    String instructorName,
    CategoryRef category,
    Instant publishedAt) {

  public static CourseSummary from(Course c) {
    return new CourseSummary(
        c.id().toString(),
        c.slug(),
        c.title(),
        c.subtitle().orElse(null),
        c.level(),
        c.language(),
        c.status(),
        c.price().amountMinor(),
        c.price().currency().getCurrencyCode(),
        c.ratingAverage(),
        c.ratingCount(),
        c.lectureCount(),
        c.totalDuration().seconds(),
        c.instructor().name(),
        CategoryRef.from(c.category()),
        c.publishedAt().orElse(null));
  }
}
