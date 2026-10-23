package com.masternova.api.identity.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.application.InstructorReviewService;
import com.masternova.api.identity.web.dto.InstructorApplicationResponse;
import com.masternova.api.identity.web.dto.PendingApplicationResponse;
import com.masternova.api.platform.CursorPage;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only: the instructor-application queue. Approve and reject are bodiless POSTs, both
 * idempotent (a double click decides once). The authorisation rule lives on
 * InstructorReviewService.
 */
@RestController
@RequestMapping("/api/v1/admin/instructor-applications")
class AdminInstructorApplicationController {

  private final InstructorReviewService review;

  AdminInstructorApplicationController(InstructorReviewService review) {
    this.review = review;
  }

  /** Pending, oldest first. The list envelope of §2; no next page while the queue is capped. */
  @GetMapping
  CursorPage<PendingApplicationResponse> pending() {
    return CursorPage.of(review.pending(), null, PendingApplicationResponse::from);
  }

  @PostMapping("/{userId}/approve")
  InstructorApplicationResponse approve(@PathVariable UUID userId, CurrentUser admin) {
    return InstructorApplicationResponse.from(review.approve(admin, userId));
  }

  @PostMapping("/{userId}/reject")
  InstructorApplicationResponse reject(@PathVariable UUID userId, CurrentUser admin) {
    return InstructorApplicationResponse.from(review.reject(admin, userId));
  }
}
