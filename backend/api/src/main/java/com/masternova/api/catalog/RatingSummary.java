package com.masternova.api.catalog;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** A course's rating as catalog stores it to show and sort by (engagement computes it). */
public record RatingSummary(UUID courseId, int count, BigDecimal average) {
  public RatingSummary {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(average, "average");
  }
}
