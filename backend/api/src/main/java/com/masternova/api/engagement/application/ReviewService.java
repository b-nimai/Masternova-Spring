package com.masternova.api.engagement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.engagement.domain.Review;
import com.masternova.api.engagement.domain.ReviewRepository;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.IdentityApi;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes reviews and keeps the rating EXACT (ADR-0023): every write locks the course's tally row
 * first, then reads the learner's old rating, then writes the review, the new tally and catalog's
 * summary — all in one transaction.
 */
@Service
public class ReviewService {

  private final Participants participants;
  private final ReviewRepository reviews;
  private final CatalogApi catalog;
  private final IdentityApi identity;
  private final Clock clock;

  ReviewService(
      Participants participants,
      ReviewRepository reviews,
      CatalogApi catalog,
      IdentityApi identity,
      Clock clock) {
    this.participants = participants;
    this.reviews = reviews;
    this.catalog = catalog;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * Creates the caller's review of the course, or rewrites it. Idempotent: the same {@code PUT}
   * twice leaves one review counted once.
   */
  @Transactional
  public Review write(CurrentUser me, UUID courseId, int rating, String body) {
    participants.requireReviewer(me, courseId); // 404 draft · 403 OWN_COURSE / NOT_ENROLLED
    String author = identity.displayName(me.id()).orElse("A learner");
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // Postgres keeps microseconds

    // ⭐ THE ORDER IS THE POINT: lock first, then read. Reading first would let two concurrent
    //    first reviews both see "none" and both add — the lock makes the second see the first.
    RatingTally tally = reviews.lockTally(courseId);
    Optional<Review> old = reviews.find(courseId, me.id());
    Review review =
        old.map(r -> r.rewritten(rating, body, author, now))
            .orElseGet(
                () ->
                    new Review(
                        UUID.randomUUID(), courseId, me.id(), rating, body, author, now, now));
    RatingTally next =
        old.map(r -> tally.replace(r.rating(), rating)).orElseGet(() -> tally.add(rating));

    reviews.save(review);
    publish(courseId, next);
    return review;
  }

  /** Deletes the caller's review. Idempotent: deleting none is fine (and changes nothing). */
  @Transactional
  public void delete(CurrentUser me, UUID courseId) {
    if (reviews.find(courseId, me.id()).isEmpty()) {
      return; // ⭐ nothing to lock for: no tally can change
    }
    RatingTally tally = reviews.lockTally(courseId);
    reviews // ⭐ under the lock: the rating it really had, deleted and returned in one statement
        .delete(courseId, me.id())
        .ifPresent(rating -> publish(courseId, tally.remove(rating)));
  }

  /** The tally and catalog's columns move together (same transaction): exact, or neither. */
  private void publish(UUID courseId, RatingTally tally) {
    reviews.saveTally(courseId, tally);
    catalog.updateRating(courseId, tally.count(), tally.average());
  }
}
