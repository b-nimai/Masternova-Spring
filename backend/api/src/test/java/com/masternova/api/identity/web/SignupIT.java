package com.masternova.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.identity.UserRegistered;
import com.masternova.api.identity.domain.UserRepository;
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
import tools.jackson.databind.json.JsonMapper;

/** Signup → outbox event → verify, through the real HTTP layer and Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired UserRepository users;
  @Autowired JsonMapper json;

  @BeforeEach
  void clean() {
    jdbc.sql("DELETE FROM outbox_message").update();
    jdbc.sql("DELETE FROM app_user").update(); // cascades to roles, tokens, sessions
  }

  private MockMvcTester.MockMvcRequestBuilder signup(String email, String password) {
    return mvc.post()
        .uri("/api/v1/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"email\":\"%s\",\"displayName\":\"Asha\",\"password\":\"%s\"}"
                .formatted(email, password));
  }

  /**
   * The raw token travels in the outbox payload — exactly what the notification module will read.
   */
  private String verificationTokenFromOutbox() {
    String payload =
        jdbc.sql("SELECT payload::text FROM outbox_message WHERE event_type = :type")
            .param("type", UserRegistered.TYPE)
            .query(String.class)
            .single();
    return json.readValue(payload, UserRegistered.class).verificationToken();
  }

  @Test
  void signupCreatesAnUnverifiedLearnerAndPublishesUserRegistered() {
    assertThat(signup("  Asha@Example.com ", "correct-horse-battery"))
        .hasStatus(HttpStatus.CREATED)
        .hasHeader("Location", "/api/v1/me")
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"email":"asha@example.com","displayName":"Asha","roles":["LEARNER"],"emailVerified":false}
            """);

    assertThat(users.findByEmail("asha@example.com")).isPresent();
    assertThat(verificationTokenFromOutbox()).hasSizeGreaterThan(40); // 256-bit token, base64url
    // the password is stored as an algorithm-tagged hash, never in clear text
    assertThat(users.findByEmail("asha@example.com").orElseThrow().passwordHash())
        .startsWith("{bcrypt}");
  }

  @Test
  void theSameEmailInAnyCaseIsTaken() {
    signup("asha@example.com", "correct-horse-battery").assertThat().hasStatus(HttpStatus.CREATED);

    assertThat(signup("ASHA@example.com", "another-password"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void invalidInputIsReportedFieldByField() {
    assertThat(signup("not-an-email", "short"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("password"); // Bean Validation runs first: too short (8–72)

    assertThat(signup("not-an-email", "long-enough-password"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("email"); // then the Email value object rejects the shape
  }

  @Test
  void theEmailedTokenVerifiesTheAccountExactlyOnce() {
    signup("asha@example.com", "correct-horse-battery").assertThat().hasStatus(HttpStatus.CREATED);
    String token = verificationTokenFromOutbox();
    String body = "{\"token\":\"%s\"}".formatted(token);

    assertThat(
            mvc.post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(users.findByEmail("asha@example.com").orElseThrow().isEmailVerified()).isTrue();

    // single use: the same link again is rejected
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VERIFICATION_TOKEN_INVALID");
  }

  @Test
  void anUnknownTokenIsRejected() {
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"made-up\"}"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
  }
}
