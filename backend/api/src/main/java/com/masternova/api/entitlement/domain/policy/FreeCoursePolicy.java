package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Anyone may watch a free course. ⭐ "Free" is price 0 AND priced ({@code CourseFacts.isFree}): an
 * unpriced course also reads 0, and giving it away would be real money.
 */
@Component
@Order(50)
class FreeCoursePolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    return context.course().isFree() ? Vote.allow(AccessGrant.FREE_COURSE) : Vote.ABSTAIN;
  }
}
