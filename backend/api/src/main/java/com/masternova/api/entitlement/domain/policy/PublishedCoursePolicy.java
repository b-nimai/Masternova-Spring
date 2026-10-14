package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nobody but staff watches a course that isn't on sale. ⭐ Except people who hold an ACTIVE
 * entitlement: they paid for it, and an instructor unpublishing to edit (PUBLISHED → DRAFT) or
 * retiring the course (ARCHIVED) must not take it away from them (found in the phase review).
 */
@Component
@Order(40)
class PublishedCoursePolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    boolean closed =
        context.course().availability() != Availability.PUBLISHED
            && !context.isStaff() // ⭐ the scope every DENY needs: the instructor sees their draft
            && !context.entitlementIs(Entitlement.Status.ACTIVE);
    return closed ? Vote.deny(DenialReason.COURSE_NOT_PUBLISHED) : Vote.ABSTAIN;
  }
}
