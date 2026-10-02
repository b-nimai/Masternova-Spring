package com.masternova.patterns.specification;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * The LEAVES: one small named rule each, in both forms. Each pair (Java, SQL) must mean the same
 * thing — the pattern's one real risk is that they drift apart, so the test checks them together.
 */
public final class ListingRules {

  private ListingRules() {}

  public static Specification<Listing> published() {
    return leaf(Listing::published, Specification.Where.of("status = 'PUBLISHED'"));
  }

  public static Specification<Listing> free() {
    return leaf(l -> l.priceMinor() == 0, Specification.Where.of("price_minor = 0"));
  }

  public static Specification<Listing> ratedAtLeast(BigDecimal min) {
    return leaf(
        l -> l.rating().compareTo(min) >= 0, Specification.Where.of("rating_average >= ?", min));
  }

  public static Specification<Listing> byInstructor(UUID id) {
    return leaf(l -> l.instructorId().equals(id), Specification.Where.of("instructor_id = ?", id));
  }

  public static Specification<Listing> titleContains(String text) {
    String needle = text.toLowerCase(Locale.ROOT);
    return leaf(
        l -> l.title().toLowerCase(Locale.ROOT).contains(needle),
        Specification.Where.of("lower(title) LIKE ? ESCAPE '\\'", "%" + escape(needle) + "%"));
  }

  /** The visibility rule: published for everyone, everything for the owner. */
  public static Specification<Listing> visibleTo(UUID viewerId) {
    return viewerId == null ? published() : published().or(byInstructor(viewerId));
  }

  static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Specification<Listing> leaf(
      java.util.function.Predicate<Listing> test, Specification.Where where) {
    return new Specification<>() {
      @Override
      public boolean isSatisfiedBy(Listing candidate) {
        return test.test(candidate);
      }

      @Override
      public Specification.Where toWhere() {
        return where;
      }
    };
  }
}
