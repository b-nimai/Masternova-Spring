package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nobody but staff watches a course that isn't on sale. ⭐ An ARCHIVED course stays open to people
 * who hold an ACTIVE entitlement: they paid for it, and retiring a course must not take it away.
 */
@Component
@Order(30)
class PublishedCoursePolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    if (context.isStaff()) {
      return Vote.ABSTAIN; // ⭐ the scope every DENY needs: the instructor sees their draft
    }
    boolean closed =
        switch (context.course().availability()) {
          case PUBLISHED -> false;
          case UNPUBLISHED -> true;
          case ARCHIVED -> context.entitlementStatus().orElse(null) != Entitlement.Status.ACTIVE;
        };
    return closed ? Vote.deny(DenialReason.COURSE_NOT_PUBLISHED) : Vote.ABSTAIN;
  }
}
