package com.masternova.api.entitlement.web;

import com.masternova.api.entitlement.application.EntitlementService;
import com.masternova.api.entitlement.web.dto.AccessResponse;
import com.masternova.api.entitlement.web.dto.CourseAccessResponse;
import com.masternova.api.identity.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * "What may I watch?" — PUBLIC routes (an anonymous visitor sees which lectures are previews); a
 * bearer token, when sent, makes the answer personal.
 */
@RestController
class AccessController {

  private final EntitlementService entitlements;

  AccessController(EntitlementService entitlements) {
    this.entitlements = entitlements;
  }

  /** The course page's locks and its call to action. */
  @GetMapping("/api/v1/courses/{courseId}/access")
  CourseAccessResponse course(@PathVariable UUID courseId, Optional<CurrentUser> viewer) {
    return CourseAccessResponse.from(entitlements.courseAccess(viewer, courseId));
  }

  /** The player route's guard (Angular): may I open this lecture? */
  @GetMapping("/api/v1/lectures/{lectureId}/access")
  AccessResponse lecture(@PathVariable UUID lectureId, Optional<CurrentUser> viewer) {
    return AccessResponse.from(entitlements.decide(viewer, lectureId));
  }
}
