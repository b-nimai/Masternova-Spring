package com.masternova.api.identity.application;

import com.masternova.api.identity.EmailVerified;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.identity.domain.VerificationToken;
import com.masternova.api.identity.domain.VerificationTokenRepository;
import com.masternova.api.identity.infrastructure.SecureTokens;
import com.masternova.api.platform.EventPublisher;
import com.masternova.api.platform.RuleViolationException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirms an email address with the token from the verification link. */
@Service
public class EmailVerificationService {

  private final VerificationTokenRepository verificationTokens;
  private final UserRepository users;
  private final SecureTokens tokens;
  private final EventPublisher events;
  private final Clock clock;

  EmailVerificationService(
      VerificationTokenRepository verificationTokens,
      UserRepository users,
      SecureTokens tokens,
      EventPublisher events,
      Clock clock) {
    this.verificationTokens = verificationTokens;
    this.users = users;
    this.tokens = tokens;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public void verify(String rawToken) {
    Instant now = clock.instant();
    VerificationToken token =
        verificationTokens
            .findByTokenHash(tokens.hash(rawToken))
            .orElseThrow(EmailVerificationService::invalid);
    if (!token.use(now)) {
      throw invalid(); // expired or already used — the same answer, deliberately
    }
    User user = users.findById(token.userId()).orElseThrow(EmailVerificationService::invalid);
    user.verifyEmail(now);
    // same transaction as the state change: the welcome email is owed exactly when this commits
    events.publish(
        new EmailVerified(user.id().toString(), user.email().value(), user.displayName()));
    // no save() calls: both entities are MANAGED — dirty checking writes them at commit (note 10
    // §5)
  }

  private static RuleViolationException invalid() {
    return new RuleViolationException(
        "VERIFICATION_TOKEN_INVALID", "This verification link is invalid or has expired.");
  }
}
