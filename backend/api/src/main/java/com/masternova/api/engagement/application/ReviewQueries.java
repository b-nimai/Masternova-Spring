package com.masternova.api.engagement.application;

import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.engagement.domain.Review;
import com.masternova.api.engagement.domain.ReviewRepository;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.CursorPage;
import com.masternova.api.platform.KeysetCursor;
import com.masternova.api.platform.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The read side of reviews: the course page's list and summary, and the learner's own. */
@Service
@Transactional(readOnly = true)
public class ReviewQueries {

  private final Participants participants;
  private final ReviewRepository reviews;

  ReviewQueries(Participants participants, ReviewRepository reviews) {
    this.participants = participants;
    this.reviews = reviews;
  }

  /** A published course's reviews, newest first: {@code limit} per page, keyset-paged. */
  public CursorPage<Review> page(UUID courseId, String cursor, int limit) {
    participants.requirePublished(courseId); // 404 for a draft: its reviews are invisible too
    return KeysetCursor.page(
        cursor,
        limit,
        (at, id, n) -> reviews.page(courseId, at, id, n),
        Review::createdAt,
        Review::id);
  }

  /** The stars behind the course card's average: empty course → all zero. */
  public RatingTally summary(UUID courseId) {
    participants.requirePublished(courseId);
    return reviews.tally(courseId).orElseGet(RatingTally::empty);
  }

  /** The caller's review of the course, or 404 {@code Review}. */
  public Review mine(CurrentUser me, UUID courseId) {
    return reviews
        .find(courseId, me.id())
        .orElseThrow(() -> new NotFoundException("Review", courseId));
  }
}
