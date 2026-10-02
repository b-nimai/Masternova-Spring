package com.masternova.api.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.identity.application.SignupService;
import com.masternova.api.identity.application.SignupService.SignupCommand;
import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.notification.UnsubscribeTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** Consent through the real HTTP layer, security chain and Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationPreferencesIT {

  @Autowired MockMvcTester mvc;
  @Autowired SignupService signup;
  @Autowired UnsubscribeTokens tokens;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private UUID userId;
  private String bearer;

  @BeforeEach
  void signedIn() throws Exception {
    jdbc.sql("DELETE FROM app_user").update(); // cascades to notification_preference
    userId =
        signup.signup(new SignupCommand("asha@example.com", "Asha", "correct-horse-battery")).id();
    MvcTestResult login =
        mvc.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"asha@example.com\",\"password\":\"correct-horse-battery\"}")
            .exchange();
    bearer =
        "Bearer "
            + json.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
  }

  private MvcTestResult put(String category, String body) {
    return mvc.put()
        .uri("/api/v1/me/notification-preferences/{category}", category)
        .header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private MvcTestResult unsubscribe(String token) {
    return mvc.post()
        .uri("/api/v1/notifications/unsubscribe")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"%s\"}".formatted(token))
        .exchange();
  }

  private String token(NotificationCategory category) {
    return tokens.issue(userId, category, Instant.now().plus(UnsubscribeTokens.TTL));
  }

  private Boolean stored(NotificationCategory category) {
    return jdbc.sql(
            "SELECT enabled FROM notification_preference WHERE user_id = :u AND category = :c")
        .param("u", userId)
        .param("c", category.name())
        .query(Boolean.class)
        .optional()
        .orElse(null);
  }

  @Test
  void aNewAccountIsSubscribedToEverythingWithoutAnyRows() {
    assertThat(
            mvc.get()
                .uri("/api/v1/me/notification-preferences")
                .header("Authorization", bearer)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            [
              {"category":"ACCOUNT_SECURITY","enabled":true,"mandatory":true},
              {"category":"PURCHASE","enabled":true,"mandatory":true},
              {"category":"COURSE_ACTIVITY","enabled":true,"mandatory":false},
              {"category":"ENGAGEMENT","enabled":true,"mandatory":false},
              {"category":"PRODUCT_NEWS","enabled":true,"mandatory":false}
            ]
            """);
    assertThat(stored(NotificationCategory.PRODUCT_NEWS)).isNull(); // ⭐ absent = subscribed
  }

  @Test
  void turningACategoryOffAndOnIsAnUpsert() {
    assertThat(put("PRODUCT_NEWS", "{\"enabled\":false}"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"category\":\"PRODUCT_NEWS\",\"enabled\":false}");
    assertThat(put("PRODUCT_NEWS", "{\"enabled\":false}")).hasStatusOk(); // same PUT again: fine
    assertThat(stored(NotificationCategory.PRODUCT_NEWS)).isFalse();

    put("PRODUCT_NEWS", "{\"enabled\":true}");
    assertThat(stored(NotificationCategory.PRODUCT_NEWS)).isTrue();
  }

  @Test
  void aMandatoryCategoryCannotBeTurnedOff() {
    assertThat(put("PURCHASE", "{\"enabled\":false}"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("CATEGORY_MANDATORY");
    assertThat(stored(NotificationCategory.PURCHASE)).isNull();
  }

  @Test
  void badInputIsA400() {
    assertThat(put("NOT_A_CATEGORY", "{\"enabled\":false}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(put("PRODUCT_NEWS", "{}")) // missing ≠ false
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("enabled");
  }

  @Test
  void preferencesNeedASignedInUser() {
    assertThat(mvc.get().uri("/api/v1/me/notification-preferences").exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theSignedLinkUnsubscribesWithoutLoggingInAndClickingTwiceIsFine() {
    String token = token(NotificationCategory.PRODUCT_NEWS);

    assertThat(unsubscribe(token))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"category\":\"PRODUCT_NEWS\"}");
    assertThat(unsubscribe(token)).hasStatusOk();
    assertThat(stored(NotificationCategory.PRODUCT_NEWS)).isFalse();
  }

  @Test
  void aForgedOrMandatoryTokenChangesNothing() {
    String token = token(NotificationCategory.PRODUCT_NEWS);
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThat(unsubscribe(tampered))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNSUBSCRIBE_TOKEN_INVALID");
    assertThat(unsubscribe(token(NotificationCategory.ACCOUNT_SECURITY)))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(stored(NotificationCategory.PRODUCT_NEWS)).isNull();
  }

  @Test
  void theOneClickHeaderEndpointAcceptsTheRfc8058FormPost() {
    String token = token(NotificationCategory.ENGAGEMENT);

    assertThat(
            mvc.post()
                .uri("/api/v1/notifications/unsubscribe/one-click?token={t}", token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("List-Unsubscribe=One-Click")
                .exchange())
        .hasStatusOk();
    assertThat(stored(NotificationCategory.ENGAGEMENT)).isFalse();
  }
}
