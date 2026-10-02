package com.masternova.api.catalog.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Collection;

/**
 * The length of a lecture in whole seconds — a VALUE OBJECT (the Phase 1 lab class, trimmed to what
 * the catalog uses). Why not {@link java.time.Duration}: it allows negatives and nanoseconds; this
 * type states the domain's rules once.
 *
 * <p>⭐ ONE column ({@code duration_seconds}), so JPA maps it with an {@code AttributeConverter}
 * ({@code infrastructure.LectureDurationConverter}, auto-applied) — not {@code @Embeddable}, which
 * is for multi-column values like {@code Money}. The entity just declares a field of this type.
 */
@DesignPattern(
    value = Pattern.VALUE_OBJECT,
    role = "ValueObject",
    note = "patterns/java/01-records-and-value-objects.md")
public record LectureDuration(int seconds) implements Comparable<LectureDuration> {

  public static final LectureDuration ZERO = new LectureDuration(0);

  public LectureDuration {
    if (seconds < 0) {
      throw new IllegalArgumentException("seconds cannot be negative: " + seconds);
    }
  }

  public static LectureDuration ofSeconds(int seconds) {
    return new LectureDuration(seconds);
  }

  public LectureDuration plus(LectureDuration other) {
    return new LectureDuration(Math.addExact(seconds, other.seconds)); // ⭐ overflow throws
  }

  /** The sum of many — a section's or a course's length. Empty → {@link #ZERO}. */
  public static LectureDuration total(Collection<LectureDuration> durations) {
    return durations.stream().reduce(ZERO, LectureDuration::plus);
  }

  @Override
  public int compareTo(LectureDuration other) {
    return Integer.compare(seconds, other.seconds);
  }
}
