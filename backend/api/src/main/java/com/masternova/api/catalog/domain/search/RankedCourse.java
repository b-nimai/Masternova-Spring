package com.masternova.api.catalog.domain.search;

import java.util.Objects;
import java.util.UUID;

/**
 * One hit of a ranker. {@code score} is the ranker's own measure (higher = better) and only means
 * something within one ranker's list: that's why fusion uses ranks, not scores.
 */
public record RankedCourse(UUID courseId, double score) {
  public RankedCourse {
    Objects.requireNonNull(courseId, "courseId");
  }
}
