package com.masternova.api.identity.application;

import com.masternova.api.identity.IdentityProperties;
import com.masternova.api.identity.domain.AuthSession;
import com.masternova.api.identity.domain.AuthSessionRepository;
import com.masternova.api.identity.domain.Email;
import com.masternova.api.identity.domain.RefreshToken;
import com.masternova.api.identity.domain.RefreshTokenRepository;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.identity.infrastructure.AccessTokenIssuer;
import com.masternova.api.identity.infrastructure.AccessTokenIssuer.AccessToken;
import com.masternova.api.identity.infrastructure.SecureTokens;
import com.masternova.api.platform.UnauthenticatedException;
import com.masternova.api.platform.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, refresh (rotation + reuse detection), logout — ADR-0006, docs/lld/identity.md §5. */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /** What a successful login/refresh hands to the web layer. */
  public record Tokens(
      User user, AccessToken accessToken, String refreshToken, Instant refreshExpiresAt) {}

  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwords;
  private final SecureTokens tokens;
  private final AccessTokenIssuer accessTokens;
  private final IdentityProperties settings;
  private final Clock clock;
  private final String dummyHash; // see login()

  AuthService(
      UserRepository users,
      AuthSessionRepository sessions,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwords,
      SecureTokens tokens,
      AccessTokenIssuer accessTokens,
      IdentityProperties settings,
      Clock clock) {
    this.users = users;
    this.sessions = sessions;
    this.refreshTokens = refreshTokens;
    this.passwords = passwords;
    this.tokens = tokens;
    this.accessTokens = accessTokens;
    this.settings = settings;
    this.clock = clock;
    this.dummyHash = passwords.encode("timing-equaliser-not-a-real-password");
  }

  @Transactional
  public Tokens login(String rawEmail, String password, String userAgent) {
    Optional<User> found;
    try {
      found = users.findByEmail(new Email(rawEmail).value());
    } catch (ValidationException malformed) {
      found = Optional.empty(); // a malformed email can't be an account — same answer as "unknown"
    }
    // ⭐ Always run ONE bcrypt comparison, even for unknown emails. Otherwise "unknown email"
    // answers
    //    in 1 ms and "wrong password" in ~100 ms — a timing oracle that reveals which emails exist.
    boolean passwordMatches =
        passwords.matches(password, found.map(User::passwordHash).orElse(dummyHash));
    if (found.isEmpty() || !passwordMatches) {
      // ⭐ identical answer for "no such account" and "wrong password" — no account probing
      throw new UnauthenticatedException("INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
    User user = found.get();
    Instant now = clock.instant();
    AuthSession session = sessions.save(AuthSession.start(user.id(), userAgent, now));
    return issueTokens(user, session, now);
  }

  /**
   * ⭐ noRollbackFor: when reuse is detected we REVOKE the session and then answer 401 by throwing.
   * By default the exception would roll the revocation back (note 09 §5) — the attacker's stolen
   * token family would survive. The revocation must commit even though the request fails.
   */
  @Transactional(noRollbackFor = UnauthenticatedException.class)
  public Tokens refresh(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw sessionExpired();
    }
    Instant now = clock.instant();
    String hash = tokens.hash(rawRefreshToken);

    if (refreshTokens.consume(hash, now) == 0) {
      // not consumable: unknown, expired — or ALREADY USED, which means someone copied it
      Optional<RefreshToken> known = refreshTokens.findByTokenHash(hash);
      if (known.isPresent() && known.get().isConsumed()) {
        sessions
            .findById(known.get().sessionId())
            .ifPresent(
                s -> {
                  s.revoke(AuthSession.RevokeReason.REUSE_DETECTED, now);
                  log.warn(
                      "Refresh token reuse detected — session {} of user {} revoked",
                      s.id(),
                      s.userId());
                });
        throw new UnauthenticatedException(
            "SESSION_REVOKED", "This session was ended for your security. Please log in again.");
      }
      throw sessionExpired();
    }

    RefreshToken consumed = refreshTokens.findByTokenHash(hash).orElseThrow();
    AuthSession session =
        sessions.findById(consumed.sessionId()).orElseThrow(AuthService::sessionExpired);
    if (!session.isActive()) {
      throw new UnauthenticatedException(
          "SESSION_REVOKED", "This session has ended. Please log in again.");
    }
    User user = users.findById(session.userId()).orElseThrow(AuthService::sessionExpired);
    session.touch(now);
    return issueTokens(user, session, now); // ⭐ rotation: a NEW refresh token in the SAME session
  }

  /** Ends the session the refresh token belongs to. Idempotent: unknown tokens are ignored. */
  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    Instant now = clock.instant();
    refreshTokens
        .findByTokenHash(tokens.hash(rawRefreshToken))
        .flatMap(t -> sessions.findById(t.sessionId()))
        .ifPresent(s -> s.revoke(AuthSession.RevokeReason.LOGOUT, now));
  }

  private Tokens issueTokens(User user, AuthSession session, Instant now) {
    String rawRefresh = tokens.generate();
    Instant refreshExpiresAt = now.plus(settings.refreshTokenTtl());
    refreshTokens.save(
        RefreshToken.issue(session.id(), tokens.hash(rawRefresh), now, refreshExpiresAt));
    return new Tokens(
        user, accessTokens.issue(user, session.id(), now), rawRefresh, refreshExpiresAt);
  }

  private static UnauthenticatedException sessionExpired() {
    return new UnauthenticatedException(
        "SESSION_EXPIRED", "Your session has expired. Please log in again.");
  }
}
