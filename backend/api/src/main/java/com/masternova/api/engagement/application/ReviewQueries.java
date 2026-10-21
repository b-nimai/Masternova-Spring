package com.masternova.api.engagement.application;

import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.engagement.domain.Review;
import com.masternova.api.engagement.domain.ReviewRepository;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.CursorPage;
import com.masternova.api.platform.NotFoundException;
import java.util.List;
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
    participants.publishedCourse(courseId); // 404 for a draft: its reviews are invisible too
    NewestFirstCursor after = NewestFirstCursor.decode(cursor);
    // ⭐ one row more than asked: it only answers "is there a next page?"
    List<Review> rows =
        reviews.page(
            courseId,
            after == null ? null : after.at(),
            after == null ? null : after.id(),
            limit + 1);
    if (rows.size() <= limit) {
      return new CursorPage<>(rows, null);
    }
    List<Review> items = rows.subList(0, limit);
    Review last = items.getLast();
    return new CursorPage<>(
        List.copyOf(items), new NewestFirstCursor(last.createdAt(), last.id()).encode());
  }

  /** The stars behind the course card's average: empty course → all zero. */
  public RatingTally summary(UUID courseId) {
    participants.publishedCourse(courseId);
    return reviews.tally(courseId).orElseGet(RatingTally::empty);
  }

  /** The caller's review of the course, or 404 {@code Review}. */
  public Review mine(CurrentUser me, UUID courseId) {
    return reviews
        .find(courseId, me.id())
        .orElseThrow(() -> new NotFoundException("Review", courseId));
  }
}
