package com.masternova.api.catalog.domain;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

/**
 * The orders a course list can be read in. ⭐ Every one ends in the primary key, in the same
 * direction: a TOTAL order, so two rows can never tie across a page boundary (ADR-0009 §2). And
 * every sort key is NOT NULL in the rows it's used on (§3), so the keyset never meets a null.
 */
public enum CourseSort {
  NEWEST("publishedAt", Direction.DESC, true),
  HIGHEST_RATED("ratingAverage", Direction.DESC, true),
  PRICE_LOW("price.amountMinor", Direction.ASC, true),
  PRICE_HIGH("price.amountMinor", Direction.DESC, true),
  /** The instructor's own list (drafts have no publishedAt) — not offered on the public catalog. */
  RECENTLY_UPDATED("updatedAt", Direction.DESC, false);

  private final String property;
  private final Direction direction;
  private final boolean publicSort;

  CourseSort(String property, Direction direction, boolean publicSort) {
    this.property = property;
    this.direction = direction;
    this.publicSort = publicSort;
  }

  /** The entity property path, e.g. {@code price.amountMinor} (a path into the embedded Money). */
  public String property() {
    return property;
  }

  public boolean isPublic() {
    return publicSort;
  }

  public Sort toSort() {
    return Sort.by(new Sort.Order(direction, property), new Sort.Order(direction, "id"));
  }

  /** This sort's key value for one course — what the next page continues after. */
  public Comparable<?> keyOf(Course course) {
    return switch (this) {
      case NEWEST -> course.publishedAt().orElseThrow(); // a published course always has one
      case HIGHEST_RATED -> course.ratingAverage();
      case PRICE_LOW, PRICE_HIGH -> course.price().amountMinor();
      case RECENTLY_UPDATED -> course.updatedAt();
    };
  }
}
