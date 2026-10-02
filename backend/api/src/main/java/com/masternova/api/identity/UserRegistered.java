package com.masternova.api.identity;

import com.masternova.kernel.event.DomainEvent;

/**
 * A new account was created. Published through the outbox in the SAME transaction as the user row;
 * the notification module (Phase 4) emails the verification link.
 *
 * <p>⚠️ Carries the raw, single-use verification token so the email can be sent from another
 * process. Acceptable because the token is single-use, expires in 24 h, lives only in the outbox
 * row (deleted by retention) — and the database itself stores just its hash.
 */
public record UserRegistered(
    String userId, String email, String displayName, String verificationToken)
    implements DomainEvent {

  public static final String TYPE = "identity.user-registered.v1";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String aggregateId() {
    return userId;
  }
}
