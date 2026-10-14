package com.masternova.api.entitlement.domain;

import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;
import java.util.Objects;

/**
 * What ONE policy says. ⭐ Three outcomes, not two: {@link Abstain} is "not my business". With a
 * boolean, "this lecture is not a preview" would have to mean deny (and lock out the buyer) or
 * allow (and open the course).
 */
public sealed interface Vote {

  record Allow(AccessGrant grant) implements Vote {
    public Allow {
      Objects.requireNonNull(grant, "grant");
    }
  }

  record Deny(DenialReason reason) implements Vote {
    public Deny {
      Objects.requireNonNull(reason, "reason");
    }
  }

  record Abstain() implements Vote {}

  Vote ABSTAIN = new Abstain();

  static Vote allow(AccessGrant grant) {
    return new Allow(grant);
  }

  static Vote deny(DenialReason reason) {
    return new Deny(reason);
  }
}
