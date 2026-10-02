package com.masternova.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.application.UserAdminService;
import com.masternova.api.identity.domain.Email;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.infrastructure.security.SecuritySliceConfig;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ⭐ A WEB SLICE: only the MVC layer + the real security chain start — no database, no
 * Testcontainers, no services (the one this controller needs is a Mockito mock). It runs in well
 * under a second and pins the HTTP CONTRACT: status codes, Problem Details, request validation, and
 * how the token becomes a {@link CurrentUser}.
 *
 * <p>What it does NOT prove: the {@code @PreAuthorize} rule on the service (the mock isn't proxied
 * by method security). That is {@code UserAdminServiceSecurityTest}'s job, and {@code RbacIT}
 * proves the whole thing end to end.
 */
@WebMvcTest(
    controllers = AdminUserController.class,
    // the idempotency filter is a platform web component the slice would pick up; it isn't under
    // test here and needs a database-backed store
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.masternova\\.api\\.platform\\.idempotency\\..*"))
@Import(SecuritySliceConfig.class)
class AdminUserControllerTest {

  private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @Autowired MockMvcTester mvc;
  @MockitoBean UserAdminService admin;

  private final User ben =
      User.register(new Email("ben@example.com"), "Ben", "{noop}x", Instant.EPOCH);

  /**
   * ⭐ spring-security-test's {@code jwt()}: puts an ALREADY-AUTHENTICATED JWT into the security
   * context — no signing, no decoder. The claims are what our resolver reads.
   */
  private static JwtRequestPostProcessor adminToken() {
    return jwt()
        .jwt(j -> j.subject(ADMIN_ID.toString()).claim("roles", List.of("ADMIN")))
        .authorities(Role.ADMIN::authority);
  }

  @Test
  void withoutATokenTheChainAnswers401AsAProblem() {
    MvcTestResult result = mvc.get().uri("/api/v1/admin/users/{id}", ben.id()).exchange();

    assertThat(result)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    // RFC 6750 challenge (Security 7 appends resource_metadata, so only the scheme is pinned)
    assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    verifyNoInteractions(admin); // ⭐ rejected before any controller ran
  }

  @Test
  void anAdminGetsAUserResponseDto() {
    given(admin.get(ben.id())).willReturn(ben);

    assertThat(mvc.get().uri("/api/v1/admin/users/{id}", ben.id()).with(adminToken()))
        .hasStatusOk()
        .bodyJson()
        .satisfies(
            body -> {
              body.assertThat().extractingPath("$.email").isEqualTo("ben@example.com");
              body.assertThat().extractingPath("$.roles").asArray().containsExactly("LEARNER");
              body.assertThat().extractingPath("$.id").isEqualTo(ben.id().toString());
            });
    // the entity's password hash never leaves the module: controllers return DTOs (CLAUDE.md §3)
    assertThat(mvc.get().uri("/api/v1/admin/users/{id}", ben.id()).with(adminToken()))
        .bodyText()
        .doesNotContain("passwordHash", "{noop}");
  }

  @Test
  void theTokenBecomesTheCurrentUserPassedToTheService() {
    given(admin.changeRoles(any(), eq(ben.id()), any())).willReturn(ben);

    assertThat(
            mvc.put()
                .uri("/api/v1/admin/users/{id}/roles", ben.id())
                .with(adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"LEARNER\",\"INSTRUCTOR\"]}"))
        .hasStatusOk();

    ArgumentCaptor<CurrentUser> actor = ArgumentCaptor.forClass(CurrentUser.class);
    verify(admin)
        .changeRoles(actor.capture(), eq(ben.id()), eq(Set.of(Role.LEARNER, Role.INSTRUCTOR)));
    assertThat(actor.getValue().id()).isEqualTo(ADMIN_ID);
    assertThat(actor.getValue().roles()).containsExactly(Role.ADMIN);
  }

  @Test
  void anInvalidBodyIs400AndNeverReachesTheService() {
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/users/{id}/roles", ben.id())
                .with(adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("roles");
    verifyNoInteractions(admin);
  }

  @Test
  void aMalformedIdIs400NotA500() {
    assertThat(mvc.get().uri("/api/v1/admin/users/not-a-uuid").with(adminToken()))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void domainExceptionsBecomeProblemDetailsWithStableCodes() {
    UUID missing = UUID.randomUUID();
    given(admin.get(missing)).willThrow(new NotFoundException("User", missing));
    given(admin.changeRoles(any(), eq(ADMIN_ID), any()))
        .willThrow(
            new RuleViolationException(
                "CANNOT_DEMOTE_SELF", "You cannot remove your own ADMIN role."));

    assertThat(mvc.get().uri("/api/v1/admin/users/{id}", missing).with(adminToken()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("NOT_FOUND");
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/users/{id}/roles", ADMIN_ID)
                .with(adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"LEARNER\"]}"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("CANNOT_DEMOTE_SELF");
  }
}
