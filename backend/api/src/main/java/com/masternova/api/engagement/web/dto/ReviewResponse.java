package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.Review;
import java.time.Instant;
import java.util.UUID;

/** One review as the course page shows it. The author is a name only, never an id. */
public record ReviewResponse(
    UUID id,
    int rating,
    String body,
    String authorName,
    Instant createdAt,
    Instant updatedAt,
    boolean edited) {

  public static ReviewResponse from(Review r) {
    return new ReviewResponse(
        r.id(), r.rating(), r.body(), r.authorName(), r.createdAt(), r.updatedAt(), r.isEdited());
  }
}
