package com.masternova.api.catalog;

import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.kernel.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * What another module may know about a course in order to SELL it (commerce, Phase 9): its name,
 * who teaches it, whether it's on sale and its price. Lighter than {@link CourseFacts}: no
 * curriculum, so a whole cart is one statement.
 */
public record CourseOffer(
    UUID courseId,
    String slug,
    String title,
    UUID instructorId,
    Availability availability,
    Money price,
    boolean priceSet) {

  public CourseOffer {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(slug, "slug");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(instructorId, "instructorId");
    Objects.requireNonNull(availability, "availability");
    Objects.requireNonNull(price, "price");
  }

  /** ⭐ Same rule as {@link CourseFacts#isFree()}: 0 alone also means "not priced yet". */
  public boolean isFree() {
    return priceSet && price.isZero();
  }
}
