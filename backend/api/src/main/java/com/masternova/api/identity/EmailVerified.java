package com.masternova.api.identity;

import com.masternova.kernel.event.DomainEvent;

/**
 * A user proved they own their address. Published through the outbox in the same transaction that
 * marks the account verified; the worker sends the welcome email.
 */
public record EmailVerified(String userId, String email, String displayName)
    implements DomainEvent {

  public static final String TYPE = "identity.email-verified.v1";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String aggregateId() {
    return userId;
  }
}
