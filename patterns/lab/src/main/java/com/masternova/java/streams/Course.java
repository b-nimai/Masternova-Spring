package com.masternova.java.streams;

import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * A course in the catalog — the element type every stream query in {@link CatalogQueries} works
 * on. A record (value object) so the test data can be compared with {@code equals}.
 */
public record Course(
    String id,
    String title,
    String category,
    String instructor,
    Money price,
    double rating, // average stars, 0..5 — a double is fine here: it's a measurement, not money
    int ratingCount,
    int enrollments,
    boolean published,
    Set<String> tags,
    LocalDate publishedOn) {

  public Course {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(instructor, "instructor");
    Objects.requireNonNull(price, "price");
    tags = Set.copyOf(tags); // ⭐ defensive copy: the record stays immutable (see note 01 §5)
  }

  public boolean isFree() {
    return price.isZero();
  }
}
