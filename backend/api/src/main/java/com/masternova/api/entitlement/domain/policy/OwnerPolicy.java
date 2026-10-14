package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The course's instructor may watch their own course, in any state. */
@Component
@Order(20)
class OwnerPolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    return context.isOwner() ? Vote.allow(AccessGrant.OWNER) : Vote.ABSTAIN;
  }
}
