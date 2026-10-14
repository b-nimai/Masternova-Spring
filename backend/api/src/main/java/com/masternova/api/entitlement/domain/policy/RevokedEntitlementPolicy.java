package com.masternova.api.entitlement.domain.policy;

import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.Vote;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A revoked entitlement (a refund, a chargeback) denies — and ⭐ because a deny always wins, it also
 * beats reasons for access written AFTER this rule: a preview lecture, a course later made free.
 * "Buy, refund, watch anyway" is closed whatever gets added next.
 */
@Component
@Order(40)
class RevokedEntitlementPolicy implements EntitlementPolicy {

  @Override
  public Vote decide(AccessContext context) {
    boolean revoked =
        !context.isStaff()
            && context.entitlementStatus().orElse(null) == Entitlement.Status.REVOKED;
    return revoked ? Vote.deny(DenialReason.ENTITLEMENT_REVOKED) : Vote.ABSTAIN;
  }
}
