package com.masternova.api.engagement.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reviews and the per-course tally. ⭐ Every write path starts with {@link #lockTally}: the course's
 * tally row is the lock all of its review writes queue on (ADR-0023 §2).
 */
public interface ReviewRepository {

  /**
   * Locks the course's tally row ({@code FOR UPDATE}) for the rest of the transaction, creating an
   * empty one first if this is the course's first review — there must be a row to lock.
   */
  RatingTally lockTally(UUID courseId);

  void saveTally(UUID courseId, RatingTally tally);

  /** The stored tally, unlocked (the summary on the course page); empty if never reviewed. */
  Optional<RatingTally> tally(UUID courseId);

  Optional<Review> find(UUID courseId, UUID userId);

  /** Inserts the review, or overwrites the learner's existing one (same id). */
  void save(Review review);

  void delete(UUID reviewId);

  /** The course's reviews, newest first, after the cursor ({@code null}: from the top). */
  List<Review> page(UUID courseId, Instant beforeAt, UUID beforeId, int limit);

  /**
   * The courses whose stored tally differs from a recount of their reviews — including tallies
   * whose reviews are all gone (a cascade from a deleted user). Unlocked: a candidate list only.
   */
  List<UUID> drifted();

  /** A recount of the course's reviews, as of NOW (call it after {@link #lockTally}). */
  RatingTally recount(UUID courseId);

  /**
   * Locks and returns a page of stored tallies in course-id order, after {@code afterCourseId}
   * ({@code null}: from the first) — the reconciler's catalog pass, one transaction per page.
   */
  List<CourseTally> lockTallies(UUID afterCourseId, int limit);

  /** Which of these courses have no stored tally (never reviewed through the code). */
  List<UUID> withoutTally(List<UUID> courseIds);

  record CourseTally(UUID courseId, RatingTally tally) {}
}
