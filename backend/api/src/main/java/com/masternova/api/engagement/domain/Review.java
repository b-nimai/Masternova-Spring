package com.masternova.api.engagement.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One learner's review of one course (at most one each: {@code review_one_each_uq}).
 *
 * @param body may be empty: a rating without words
 * @param authorName the display name when it was last written (a snapshot: the course page never
 *     calls identity)
 */
public record Review(
    UUID id,
    UUID courseId,
    UUID userId,
    int rating,
    String body,
    String authorName,
    Instant createdAt,
    Instant updatedAt) {

  public static final int MAX_BODY = 2000;

  public Review {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(userId, "userId");
    if (rating < RatingTally.MIN_RATING || rating > RatingTally.MAX_RATING) {
      throw new IllegalArgumentException("a rating is 1–5 stars: " + rating);
    }
    body = body == null ? "" : body.strip();
    if (body.length() > MAX_BODY) {
      throw new IllegalArgumentException("a review is at most " + MAX_BODY + " characters");
    }
    Objects.requireNonNull(authorName, "authorName");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** This review rewritten at {@code now}: same id and first-written time. */
  public Review rewritten(int newRating, String newBody, String newAuthorName, Instant now) {
    return new Review(id, courseId, userId, newRating, newBody, newAuthorName, createdAt, now);
  }

  public boolean isEdited() {
    return updatedAt.isAfter(createdAt);
  }
}
