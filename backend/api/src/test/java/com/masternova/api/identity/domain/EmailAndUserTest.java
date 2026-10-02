package com.masternova.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.identity.Role;
import com.masternova.api.platform.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAndUserTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  @Test
  void emailsAreNormalisedSoOnePersonIsOneAccount() {
    assertThat(new Email("  Asha@Example.COM ")).isEqualTo(new Email("asha@example.com"));
    assertThat(new Email("Asha@Example.com").value()).isEqualTo("asha@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "asha", "asha@", "@example.com", "asha@example", "a b@example.com"})
  void invalidEmailsCannotExist(String raw) {
    assertThatThrownBy(() -> new Email(raw))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("invalid fields");
  }

  @Test
  void aNewUserIsAnUnverifiedLearner() {
    User user = User.register(new Email("asha@example.com"), "  Asha  ", "{bcrypt}hash", NOW);

    assertThat(user.roles()).containsExactly(Role.LEARNER);
    assertThat(user.displayName()).isEqualTo("Asha");
    assertThat(user.isEmailVerified()).isFalse();
  }

  @Test
  void aUserAlwaysKeepsAtLeastOneRole() {
    User user = User.register(new Email("asha@example.com"), "Asha", "{bcrypt}hash", NOW);
    user.grant(Role.INSTRUCTOR);
    user.revoke(Role.LEARNER);

    assertThat(user.roles()).containsExactly(Role.INSTRUCTOR);
    assertThatThrownBy(() -> user.revoke(Role.INSTRUCTOR))
        .hasMessageContaining("at least one role");
  }

  @Test
  void rolesCannotBeChangedFromOutside() {
    User user = User.register(new Email("asha@example.com"), "Asha", "{bcrypt}hash", NOW);

    assertThatThrownBy(() -> user.roles().add(Role.ADMIN))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void verificationHappensOnce() {
    User user = User.register(new Email("asha@example.com"), "Asha", "{bcrypt}hash", NOW);

    user.verifyEmail(NOW);
    user.verifyEmail(NOW.plusSeconds(60)); // idempotent

    assertThat(user.isEmailVerified()).isTrue();
  }

  @Test
  void aRevokedSessionStaysRevokedWithItsFirstReason() {
    AuthSession session = AuthSession.start(java.util.UUID.randomUUID(), "Firefox", NOW);

    session.revoke(AuthSession.RevokeReason.REUSE_DETECTED, NOW);
    session.revoke(AuthSession.RevokeReason.LOGOUT, NOW.plusSeconds(5));

    assertThat(session.isActive()).isFalse();
    assertThat(session.revokeReason()).isEqualTo(AuthSession.RevokeReason.REUSE_DETECTED);
  }
}
