package com.masternova.api.identity.application;

import com.masternova.api.identity.IdentityProperties;
import com.masternova.api.identity.UserRegistered;
import com.masternova.api.identity.domain.Email;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.identity.domain.VerificationToken;
import com.masternova.api.identity.domain.VerificationTokenRepository;
import com.masternova.api.identity.infrastructure.SecureTokens;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates accounts. One transaction: user + verification token hash + outbox event. */
@Service
public class SignupService {

  private final UserRepository users;
  private final VerificationTokenRepository verificationTokens;
  private final PasswordEncoder passwords;
  private final SecureTokens tokens;
  private final EventPublisher events;
  private final IdentityProperties settings;
  private final Clock clock;

  SignupService(
      UserRepository users,
      VerificationTokenRepository verificationTokens,
      PasswordEncoder passwords,
      SecureTokens tokens,
      EventPublisher events,
      IdentityProperties settings,
      Clock clock) {
    this.users = users;
    this.verificationTokens = verificationTokens;
    this.passwords = passwords;
    this.tokens = tokens;
    this.events = events;
    this.settings = settings;
    this.clock = clock;
  }

  public record SignupCommand(String email, String displayName, String password) {}

  @Transactional // ⭐ the unit of work: user, token and event commit together (pattern 16, 17)
  public User signup(SignupCommand command) {
    Email email = new Email(command.email()); // normalises + validates (400 if malformed)
    if (users.existsByEmail(email.value())) {
      throw emailTaken();
    }
    Instant now = clock.instant();
    User user =
        User.register(email, command.displayName(), passwords.encode(command.password()), now);
    try {
      users.saveAndFlush(user); // flush NOW so a concurrent duplicate fails here, inside our try
    } catch (DataIntegrityViolationException raceLost) {
      // two signups with one email at the same instant: the unique constraint picked a winner
      throw emailTaken();
    }

    String rawToken = tokens.generate();
    verificationTokens.save(
        VerificationToken.issue(
            user.id(),
            tokens.hash(rawToken),
            VerificationToken.Purpose.EMAIL_VERIFICATION,
            now.plus(settings.verificationTokenTtl())));
    events.publish(
        new UserRegistered(user.id().toString(), email.value(), user.displayName(), rawToken));
    return user;
  }

  private static ConflictException emailTaken() {
    return new ConflictException("EMAIL_TAKEN", "An account with this email already exists.");
  }
}
