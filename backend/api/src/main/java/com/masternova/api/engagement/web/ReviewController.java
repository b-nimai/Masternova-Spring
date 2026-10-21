package com.masternova.api.engagement.web;

import com.masternova.api.engagement.application.ReviewQueries;
import com.masternova.api.engagement.web.dto.RatingSummaryResponse;
import com.masternova.api.engagement.web.dto.ReviewResponse;
import com.masternova.api.platform.CursorPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A published course's reviews, public like the rest of its page. */
@RestController
@RequestMapping("/api/v1/courses/{courseId}/reviews")
class ReviewController {

  private final ReviewQueries reviews;

  ReviewController(ReviewQueries reviews) {
    this.reviews = reviews;
  }

  /** Newest first, keyset-paged (API conventions §2). */
  @GetMapping
  CursorPage<ReviewResponse> list(
      @PathVariable UUID courseId,
      @RequestParam(required = false) @Size(max = 300) String cursor,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
    var page = reviews.page(courseId, cursor, limit);
    return CursorPage.of(page.items(), page.nextCursor(), ReviewResponse::from);
  }

  @GetMapping("/summary")
  RatingSummaryResponse summary(@PathVariable UUID courseId) {
    return RatingSummaryResponse.from(reviews.summary(courseId));
  }
}
