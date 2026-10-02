package com.masternova.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.masternova.api.identity.domain.Email;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * ⭐ Method security in isolation: a TINY Spring context — just the service and
 * {@code @EnableMethodSecurity}, no web, no database. {@code @PreAuthorize} only works through the
 * proxy Spring wraps around the bean (note 09), so the test needs Spring — but nothing else.
 *
 * <p>{@code @WithMockUser} puts a user with the given roles into the SecurityContext for one test.
 */
@SpringJUnitConfig(UserAdminServiceSecurityTest.Config.class)
class UserAdminServiceSecurityTest {

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  @Import(UserAdminService.class)
  static class Config {}

  @Autowired UserAdminService admin;
  @MockitoBean UserRepository users;

  private final User ben =
      User.register(new Email("ben@example.com"), "Ben", "{noop}x", Instant.EPOCH);

  @Test
  @WithMockUser(roles = "ADMIN")
  void anAdminPasses() {
    given(users.findById(ben.id())).willReturn(Optional.of(ben));
    assertThat(admin.get(ben.id())).isSameAs(ben);
  }

  @Test
  @WithMockUser(roles = {"LEARNER", "INSTRUCTOR"})
  void anyoneElseIsDeniedBeforeTheMethodBodyRuns() {
    assertThatThrownBy(() -> admin.get(ben.id())).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(users); // ⭐ the proxy stopped the call: the repository was never touched
  }

  @Test
  void withNoAuthenticationAtAllItFailsClosed() {
    assertThatThrownBy(() -> admin.get(ben.id()))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }
}
