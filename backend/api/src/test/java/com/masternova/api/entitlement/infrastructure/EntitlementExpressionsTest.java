package com.masternova.api.entitlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.platform.ReasonedDenial;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * ⭐ The SpEL hook in isolation: method security + the {@code @entitlements} bean + a guarded
 * method, with the engine itself mocked. Proves the denial's REASON survives Spring's proxy — the
 * thing a {@code boolean} expression would lose.
 */
@SpringJUnitConfig(EntitlementExpressionsTest.Config.class)
class EntitlementExpressionsTest {

  /** Any module's method, guarded by the engine. */
  static class Lectures {
    private final AtomicInteger opened = new AtomicInteger();

    @PreAuthorize("@entitlements.canWatch(authentication, #lectureId)")
    void open(UUID lectureId) {
      opened.incrementAndGet();
    }

    /** ⭐ A method, not a field: the bean is a CGLIB proxy whose own fields are never set. */
    int opened() {
      return opened.get();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  @Import(EntitlementExpressions.class)
  static class Config {
    @Bean
    Lectures lectures() {
      return new Lectures();
    }
  }

  static final UUID LECTURE = UUID.randomUUID();
  static final UUID LEARNER = UUID.randomUUID();

  @Autowired Lectures lectures;
  @MockitoBean EntitlementApi entitlements;

  private static void signedIn() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject(LEARNER.toString())
            .claim("roles", List.of("LEARNER"))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void anAllowedViewerGetsIn() {
    signedIn();
    given(entitlements.decide(any(), eq(LECTURE)))
        .willReturn(new AccessDecision.Allowed(AccessGrant.ENTITLED));

    int before = lectures.opened(); // the bean is shared by every test in the class
    lectures.open(LECTURE);

    assertThat(lectures.opened()).isEqualTo(before + 1);
  }

  @Test
  void aDenialCarriesItsReasonThroughTheProxy() {
    signedIn();
    given(entitlements.decide(any(), eq(LECTURE)))
        .willReturn(new AccessDecision.Denied(DenialReason.ENTITLEMENT_REVOKED));

    int before = lectures.opened();
    assertThatThrownBy(() -> lectures.open(LECTURE))
        .isInstanceOfSatisfying(
            AuthorizationDeniedException.class,
            e ->
                assertThat(((ReasonedDenial) e.getAuthorizationResult()).denialReason())
                    .isEqualTo("ENTITLEMENT_REVOKED"));
    assertThat(lectures.opened()).isEqualTo(before); // ⭐ the method body never ran
  }

  @Test
  void theViewerIsBuiltFromTheToken() {
    signedIn();
    given(entitlements.decide(any(), eq(LECTURE)))
        .willAnswer(
            i -> {
              @SuppressWarnings("unchecked")
              var viewer =
                  (java.util.Optional<com.masternova.api.identity.CurrentUser>) i.getArgument(0);
              return viewer.map(u -> u.id().equals(LEARNER)).orElse(false)
                  ? new AccessDecision.Allowed(AccessGrant.ENTITLED)
                  : new AccessDecision.Denied(DenialReason.NO_ENTITLEMENT);
            });

    int before = lectures.opened(); // the bean is shared by every test in the class
    lectures.open(LECTURE);

    assertThat(lectures.opened()).isEqualTo(before + 1);
  }
}
