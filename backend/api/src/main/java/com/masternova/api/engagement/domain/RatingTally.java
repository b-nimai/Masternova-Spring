package com.masternova.api.engagement.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * A course's ratings as FIVE INTEGER COUNTERS, one per star (ADR-0023 §1). ⭐ Count, sum and average
 * are derived on demand, never stored beside the stars, so nothing can disagree with them; and
 * integers don't drift the way an average updated from the previous average does.
 *
 * <p>Immutable: {@link #add}, {@link #remove} and {@link #replace} return a new tally.
 */
@DesignPattern(
    value = Pattern.VALUE_OBJECT,
    role = "ValueObject",
    note = "patterns/java/01-records-and-value-objects.md")
public record RatingTally(int[] stars) {

  public static final int MIN_RATING = 1;
  public static final int MAX_RATING = 5;

  public RatingTally {
    if (stars.length != MAX_RATING) {
      throw new IllegalArgumentException("a tally has one counter per star: 5");
    }
    stars = stars.clone(); // ⭐ an array component is mutable: copy in, copy out
    for (int n : stars) {
      if (n < 0) {
        throw new IllegalArgumentException("a star counter can't go below 0: " + n);
      }
    }
  }

  public static RatingTally empty() {
    return new RatingTally(new int[MAX_RATING]);
  }

  public static RatingTally of(int s1, int s2, int s3, int s4, int s5) {
    return new RatingTally(new int[] {s1, s2, s3, s4, s5});
  }

  /** The counters, 1★ first. A copy: the tally stays immutable. */
  @Override
  public int[] stars() {
    return stars.clone();
  }

  /** How many learners gave {@code rating} stars. */
  public int starsOf(int rating) {
    return stars[index(rating)];
  }

  public RatingTally add(int rating) {
    return moved(rating, +1);
  }

  /**
   * @throws IllegalArgumentException if no review with this rating is counted — removing it would
   *     mean the tally was already wrong
   */
  public RatingTally remove(int rating) {
    return moved(rating, -1);
  }

  /** An edited review: one counter down, another up (the same one: no change). */
  public RatingTally replace(int oldRating, int newRating) {
    return remove(oldRating).add(newRating);
  }

  public int count() {
    return Arrays.stream(stars).sum();
  }

  public long sum() {
    long sum = 0;
    for (int i = 0; i < stars.length; i++) {
      sum += (long) (i + 1) * stars[i];
    }
    return sum;
  }

  /** {@code sum / count} to 2 places, HALF_UP (the catalog column's scale); 0.00 when empty. */
  public BigDecimal average() {
    int count = count();
    return count == 0
        ? BigDecimal.ZERO.setScale(2)
        : BigDecimal.valueOf(sum()).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
  }

  private RatingTally moved(int rating, int delta) {
    int[] next = stars.clone();
    next[index(rating)] += delta;
    return new RatingTally(next); // ⭐ the constructor refuses a counter below 0
  }

  private static int index(int rating) {
    if (rating < MIN_RATING || rating > MAX_RATING) {
      throw new IllegalArgumentException("a rating is 1–5 stars: " + rating);
    }
    return rating - 1;
  }

  // ⭐ a record with an array component compares the array by IDENTITY: compare the contents
  @Override
  public boolean equals(Object o) {
    return o instanceof RatingTally other && Arrays.equals(stars, other.stars);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(stars);
  }

  @Override
  public String toString() {
    return "RatingTally" + Arrays.toString(stars);
  }
}
