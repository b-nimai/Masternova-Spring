package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Admins may watch anything (support, moderation). */
@Component
@Order(10)
class AdminPolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    return context.viewer().admin() ? Vote.allow(AccessGrant.ADMIN) : Vote.ABSTAIN;
  }
}
