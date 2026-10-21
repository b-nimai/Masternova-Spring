package com.masternova.api.engagement.web;

import com.masternova.api.engagement.application.ReviewQueries;
import com.masternova.api.engagement.application.ReviewService;
import com.masternova.api.engagement.web.dto.ReviewResponse;
import com.masternova.api.engagement.web.dto.WriteReviewRequest;
import com.masternova.api.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own review of a course. ⭐ PUT and DELETE on the pair: idempotent by nature (one
 * review each), so retries need no Idempotency-Key.
 */
@RestController
@RequestMapping("/api/v1/me/reviews/{courseId}")
class MyReviewController {

  private final ReviewService reviews;
  private final ReviewQueries queries;

  MyReviewController(ReviewService reviews, ReviewQueries queries) {
    this.reviews = reviews;
    this.queries = queries;
  }

  /** 404 {@code Review} when the caller hasn't reviewed it. */
  @GetMapping
  ReviewResponse mine(CurrentUser me, @PathVariable UUID courseId) {
    return ReviewResponse.from(queries.mine(me, courseId));
  }

  /** 403 {@code NOT_ENROLLED} / {@code OWN_COURSE}; 404 for a draft. */
  @PutMapping
  ReviewResponse write(
      CurrentUser me, @PathVariable UUID courseId, @Valid @RequestBody WriteReviewRequest body) {
    return ReviewResponse.from(reviews.write(me, courseId, body.rating(), body.body()));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(CurrentUser me, @PathVariable UUID courseId) {
    reviews.delete(me, courseId);
  }
}
