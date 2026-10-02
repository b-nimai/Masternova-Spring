package com.masternova.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.identity.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identity's JPA mapping against the real Flyway schema (Hibernate validates every column at
 * startup — a mapping mistake fails here, not in production).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class IdentityPersistenceIT {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  @Autowired UserRepository users;
  @Autowired AuthSessionRepository sessions;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired TestEntityManager em;

  private User newUser(String email) {
    return User.register(new Email(email), "Asha", "{bcrypt}hash", NOW);
  }

  @Test
  void aUserRoundTripsWithRoles() {
    User user = newUser("asha@example.com");
    user.grant(Role.INSTRUCTOR);
    users.saveAndFlush(user);
    em.clear();

    User loaded = users.findByEmail("asha@example.com").orElseThrow();
    assertThat(loaded.roles()).containsExactlyInAnyOrder(Role.LEARNER, Role.INSTRUCTOR);
    assertThat(loaded.email()).isEqualTo(new Email("ASHA@example.com"));
  }

  @Test
  void theDatabaseRejectsADuplicateEmail() {
    users.saveAndFlush(newUser("asha@example.com"));

    // a second account with the same NORMALISED email — the unique constraint is the last line of
    // defence
    assertThatThrownBy(() -> users.saveAndFlush(newUser("Asha@Example.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aRefreshTokenCanBeConsumedExactlyOnce() {
    User user = users.saveAndFlush(newUser("asha@example.com"));
    AuthSession session = sessions.saveAndFlush(AuthSession.start(user.id(), "Firefox", NOW));
    refreshTokens.saveAndFlush(
        RefreshToken.issue(session.id(), "a".repeat(64), NOW, NOW.plus(Duration.ofDays(30))));

    assertThat(refreshTokens.consume("a".repeat(64), NOW)).isEqualTo(1); // this request wins
    assertThat(refreshTokens.consume("a".repeat(64), NOW)).isZero(); //    a replay gets nothing
    assertThat(refreshTokens.findByTokenHash("a".repeat(64)).orElseThrow().isConsumed()).isTrue();
  }
}
