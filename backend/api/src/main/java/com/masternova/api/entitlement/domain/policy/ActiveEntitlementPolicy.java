package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A viewer with an ACTIVE entitlement (bought, enrolled, granted) may watch. ⭐ Expired or revoked
 * rows ABSTAIN here, they don't deny: an expired rental must not block a course that became free.
 * (Revoked has its own denying rule; expired only shapes the fallback reason.)
 */
@Component
@Order(70)
class ActiveEntitlementPolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    return context.entitlementIs(Entitlement.Status.ACTIVE)
        ? Vote.allow(AccessGrant.ENTITLED)
        : Vote.ABSTAIN;
  }
}
