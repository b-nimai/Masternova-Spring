package com.masternova.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.identity.application.SignupService;
import com.masternova.api.identity.application.SignupService.SignupCommand;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Login, deny-by-default, refresh rotation, reuse detection and logout — ADR-0006. */
@SpringBootTest(properties = "masternova.outbox.relay-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIT {

  @Autowired MockMvcTester mvc;
  @Autowired SignupService signup;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired JwtEncoder jwtEncoder;

  @BeforeEach
  void aRegisteredUser() {
    jdbc.sql("DELETE FROM app_user").update();
    signup.signup(new SignupCommand("asha@example.com", "Asha", "correct-horse-battery"));
  }

  private MvcTestResult login(String email, String password) {
    return mvc.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
        .exchange();
  }

  private MvcTestResult refresh(String refreshToken) {
    return mvc.post()
        .uri("/api/v1/auth/refresh")
        .cookie(new Cookie("mn_refresh", refreshToken))
        .exchange();
  }

  private static String refreshCookie(MvcTestResult result) {
    return result.getResponse().getCookie("mn_refresh").getValue();
  }

  private String accessToken(MvcTestResult result) throws Exception {
    JsonNode body = json.readTree(result.getResponse().getContentAsString());
    return body.get("accessToken").asString();
  }

  @Test
  void loginReturnsAnAccessTokenAndAHardenedRefreshCookie() throws Exception {
    MvcTestResult result = login("ASHA@example.com", "correct-horse-battery");

    assertThat(result).hasStatus(HttpStatus.OK).hasHeader("Cache-Control", "no-store");
    assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.user.roles")
        .asArray()
        .containsExactly("LEARNER");
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie)
        .contains("mn_refresh=")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Path=/api/v1/auth");
    // the refresh token is NOT in the JSON body — only in the httpOnly cookie
    assertThat(result.getResponse().getContentAsString()).doesNotContain(refreshCookie(result));
  }

  @Test
  void theAccessTokenOpensProtectedEndpoints() throws Exception {
    String token = accessToken(login("asha@example.com", "correct-horse-battery"));

    assertThat(mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.email")
        .isEqualTo("asha@example.com");
    assertThat(mvc.get().uri("/api/v1/me"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
    MvcTestResult wrongPassword = login("asha@example.com", "wrong-password-123");
    MvcTestResult unknownEmail = login("nobody@example.com", "whatever-password");

    assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPassword.getResponse().getContentAsString().replace("/api/v1/auth/login", ""))
        .isEqualTo(
            unknownEmail.getResponse().getContentAsString().replace("/api/v1/auth/login", ""));
  }

  @Test
  void tamperedAndExpiredTokensAreRejected() throws Exception {
    String token = accessToken(login("asha@example.com", "correct-horse-battery"));
    String tampered = token.substring(0, token.length() - 4) + "AAAA";
    JwtClaimsSet expiredClaims =
        JwtClaimsSet.builder()
            .issuer("masternova")
            .subject("x")
            .issuedAt(Instant.now().minusSeconds(3600))
            .expiresAt(Instant.now().minusSeconds(600))
            .build();
    String expired =
        jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), expiredClaims))
            .getTokenValue();

    assertThat(mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + tampered))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + expired))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void refreshRotatesTheTokenEveryTime() throws Exception {
    String r1 = refreshCookie(login("asha@example.com", "correct-horse-battery"));

    MvcTestResult second = refresh(r1);
    String r2 = refreshCookie(second);
    MvcTestResult third = refresh(r2);

    assertThat(second).hasStatus(HttpStatus.OK);
    assertThat(third).hasStatus(HttpStatus.OK);
    assertThat(r2).isNotEqualTo(r1);
    assertThat(refreshCookie(third)).isNotEqualTo(r2);
    assertThat(accessToken(third)).isNotBlank();
  }

  @Test
  void reusingAnOldRefreshTokenRevokesTheWholeSession() {
    String stolen = refreshCookie(login("asha@example.com", "correct-horse-battery"));
    String legit =
        refreshCookie(refresh(stolen)); // the real user refreshed: `stolen` is now consumed

    // the attacker replays the copied token …
    assertThat(refresh(stolen))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("SESSION_REVOKED");
    // … and the WHOLE family dies — including the legitimate user's newer token (ADR-0006)
    assertThat(refresh(legit)).hasStatus(HttpStatus.UNAUTHORIZED);
    // the revocation was COMMITTED despite the 401 (noRollbackFor)
    assertThat(
            jdbc.sql("SELECT count(*) FROM auth_session WHERE revoke_reason = 'REUSE_DETECTED'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void logoutEndsTheSessionAndClearsTheCookie() {
    String token = refreshCookie(login("asha@example.com", "correct-horse-battery"));

    MvcTestResult logout =
        mvc.post().uri("/api/v1/auth/logout").cookie(new Cookie("mn_refresh", token)).exchange();

    assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
    assertThat(refresh(token)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void refreshWithoutACookieIs401() {
    assertThat(mvc.post().uri("/api/v1/auth/refresh"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("SESSION_EXPIRED");
  }

  @Test
  void twoTabsRefreshingAtOnceLookLikeReuse() throws InterruptedException {
    // Documents the known limitation (ADR-0006): the client MUST single-flight its refreshes.
    String token = refreshCookie(login("asha@example.com", "correct-horse-battery"));
    List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch start = new CountDownLatch(1);
    try (var tabs = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < 2; i++) {
        tabs.submit(
            () -> {
              start.await();
              statuses.add(refresh(token).getResponse().getStatus());
              return null;
            });
      }
      start.countDown();
    }
    // exactly one wins the atomic consume; the other is treated as a replay
    assertThat(statuses).containsExactlyInAnyOrder(200, 401);
  }
}
