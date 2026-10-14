package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Anyone may watch a lecture the instructor marked as a preview. Abstains on the course-wide
 * question (no lecture): a preview opens one lecture, not the course.
 */
@Component
@Order(60)
class PreviewLecturePolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    return context.lecture().filter(LectureFacts::preview).isPresent()
        ? Vote.allow(AccessGrant.PREVIEW_LECTURE)
        : Vote.ABSTAIN;
  }
}
