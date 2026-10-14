package com.masternova.api.entitlement;

import java.util.Objects;

/**
 * The entitlement engine's answer — part of the module API.
 *
 * <p>⭐ Sealed with exactly two outcomes: unlike a policy's vote, the reduced decision can't
 * abstain. Callers {@code switch} over it exhaustively, and each outcome carries its reason.
 */
public sealed interface AccessDecision {

  record Allowed(AccessGrant grant) implements AccessDecision {
    public Allowed {
      Objects.requireNonNull(grant, "grant");
    }
  }

  record Denied(DenialReason reason) implements AccessDecision {
    public Denied {
      Objects.requireNonNull(reason, "reason");
    }
  }

  default boolean isAllowed() {
    return this instanceof Allowed;
  }
}
