package com.masternova.java.valueobject;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The length of a lecture video in whole seconds: a <b>Value Object</b>.
 *
 * <p>Why not {@link java.time.Duration}? It allows negatives and nanoseconds and knows nothing
 * about the {@code "1:02:03"} format the UI shows. A small domain type states those rules once.
 * (Naming it {@code Duration} would clash with {@code java.time.Duration} — pick domain names.)
 *
 * <p>Study note: {@code patterns/java/01-records-and-value-objects.md}.
 */
public record LectureDuration(int seconds) implements Comparable<LectureDuration> {

  // ⭐ A static constant on a record — fine (records can't have extra INSTANCE fields, but
  //    static fields are allowed). Value objects are immutable, so sharing one instance is safe.
  public static final LectureDuration ZERO = new LectureDuration(0);

  // ⭐ Compile regexes ONCE (static final). Pattern.compile is expensive; a Pattern is immutable
  //    and thread-safe, a Matcher is not (create one per call).
  //    (\d+)        one or more digits, captured        → group 1
  //    ([0-5]\d)    exactly two digits, 00..59, captured → seconds can't be "60" or "5"
  private static final Pattern MINUTES_SECONDS = Pattern.compile("(\\d+):([0-5]\\d)");
  private static final Pattern HOURS_MINUTES_SECONDS =
      Pattern.compile("(\\d+):([0-5]\\d):([0-5]\\d)");

  public LectureDuration {
    if (seconds < 0) {
      throw new IllegalArgumentException("seconds cannot be negative: " + seconds);
    }
  }

  public static LectureDuration ofSeconds(int seconds) {
    return new LectureDuration(seconds);
  }

  public static LectureDuration ofMinutes(int minutes) {
    // multiplyExact: Integer.MAX_VALUE minutes overflows int → ArithmeticException, not garbage.
    // A negative input is caught by the compact constructor.
    return new LectureDuration(Math.multiplyExact(minutes, 60));
  }

  /** Parses {@code "m:ss"} or {@code "h:mm:ss"}; anything else → IllegalArgumentException. */
  public static LectureDuration parse(String text) {
    Objects.requireNonNull(text, "text");

    // ⭐ matcher(...).matches() checks the WHOLE string (find() would accept " 4:05" or "x4:05").
    Matcher hms = HOURS_MINUTES_SECONDS.matcher(text);
    if (hms.matches()) {
      int hours = Integer.parseInt(hms.group(1));
      int minutes = Integer.parseInt(hms.group(2));
      int secs = Integer.parseInt(hms.group(3));
      return ofSeconds(Math.addExact(Math.multiplyExact(hours, 3600), minutes * 60 + secs));
    }

    Matcher ms = MINUTES_SECONDS.matcher(text);
    if (ms.matches()) {
      int minutes = Integer.parseInt(ms.group(1)); // may exceed 59: "75:00" is fine
      int secs = Integer.parseInt(ms.group(2));
      return ofSeconds(Math.addExact(Math.multiplyExact(minutes, 60), secs));
    }

    throw new IllegalArgumentException("expected m:ss or h:mm:ss, got \"" + text + "\"");
  }

  /** {@code "4:05"}, {@code "1:02:03"}, {@code "0:00"} — hours only when there is at least one. */
  public String format() {
    int hours = seconds / 3600;
    int minutes = seconds % 3600 / 60;
    int secs = seconds % 60;
    // ⭐ String.formatted (Java 15+) = String.format with the template first.
    //    %02d → at least 2 digits, zero-padded: 5 → "05".
    return hours > 0
        ? "%d:%02d:%02d".formatted(hours, minutes, secs)
        : "%d:%02d".formatted(minutes, secs);
  }

  public LectureDuration plus(LectureDuration other) {
    return new LectureDuration(Math.addExact(seconds, other.seconds));
  }

  /** Total length of a section. */
  public static LectureDuration total(List<LectureDuration> durations) {
    // ⭐ reduce(identity, accumulator): start from ZERO and fold with plus.
    //    The identity makes the empty list return ZERO instead of an Optional.
    //    LectureDuration::plus is a METHOD REFERENCE = (a, b) -> a.plus(b).
    return durations.stream().reduce(ZERO, LectureDuration::plus);
  }

  /** Bridge to the JDK type when an API needs it — the domain type stays in charge. */
  public Duration toJavaDuration() {
    return Duration.ofSeconds(seconds);
  }

  @Override
  public int compareTo(LectureDuration other) {
    return Integer.compare(seconds, other.seconds);
  }
}
