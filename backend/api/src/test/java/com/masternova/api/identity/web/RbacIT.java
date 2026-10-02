package com.masternova.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.application.SignupService;
import com.masternova.api.identity.application.SignupService.SignupCommand;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import jakarta.servlet.http.Cookie;
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

/** Role-based access: who may change roles, and how a new role reaches the token. */
@SpringBootTest(properties = "masternova.outbox.relay-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RbacIT {

  @Autowired MockMvcTester mvc;
  @Autowired SignupService signup;
  @Autowired UserRepository users;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private UUID learnerId;
  private UUID adminId;

  @BeforeEach
  void usersExist() {
    jdbc.sql("DELETE FROM app_user").update();
    learnerId =
        signup.signup(new SignupCommand("learner@example.com", "Lee", "learner-password-1")).id();
    User admin = signup.signup(new SignupCommand("admin@example.com", "Ada", "admin-password-123"));
    adminId = admin.id();
    jdbc.sql("INSERT INTO app_user_role (user_id, role) VALUES (:id, 'ADMIN')")
        .param("id", adminId)
        .update();
  }

  private MvcTestResult login(String email, String password) {
    return mvc.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
        .exchange();
  }

  private String bearer(MvcTestResult login) throws Exception {
    return "Bearer "
        + json.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
  }

  private MvcTestResult changeRoles(String bearer, UUID userId, String rolesJson) {
    return mvc.put()
        .uri("/api/v1/admin/users/{id}/roles", userId)
        .header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roles\":" + rolesJson + "}")
        .exchange();
  }

  @Test
  void aLearnerCannotUseAdminEndpoints() throws Exception {
    String learner = bearer(login("learner@example.com", "learner-password-1"));

    assertThat(changeRoles(learner, learnerId, "[\"ADMIN\"]"))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"FORBIDDEN","reason":"INSUFFICIENT_ROLE"}
            """);
    assertThat(users.findById(learnerId).orElseThrow().roles()).containsExactly(Role.LEARNER);
  }

  @Test
  void anAdminPromotesALearnerAndTheNewRoleArrivesWithTheNextRefresh() throws Exception {
    String admin = bearer(login("admin@example.com", "admin-password-123"));
    MvcTestResult learnerLogin = login("learner@example.com", "learner-password-1");
    String learnerRefresh = learnerLogin.getResponse().getCookie("mn_refresh").getValue();

    assertThat(changeRoles(admin, learnerId, "[\"LEARNER\",\"INSTRUCTOR\"]"))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.roles")
        .asArray()
        .containsExactly("LEARNER", "INSTRUCTOR");

    // the learner's CURRENT token still says LEARNER (roles live in the JWT) …
    assertThat(learnerLogin)
        .bodyJson()
        .extractingPath("$.user.roles")
        .asArray()
        .containsExactly("LEARNER");
    // … the next refresh mints a token with the new role (ADR-0006: ≤ 15 min lag)
    assertThat(
            mvc.post().uri("/api/v1/auth/refresh").cookie(new Cookie("mn_refresh", learnerRefresh)))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.user.roles")
        .asArray()
        .containsExactly(
            "LEARNER", "INSTRUCTOR"); // enum DECLARATION order (an enum's natural order)
  }

  @Test
  void anAdminCannotRemoveTheirOwnAdminRole() throws Exception {
    String admin = bearer(login("admin@example.com", "admin-password-123"));

    assertThat(changeRoles(admin, adminId, "[\"LEARNER\"]"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("CANNOT_DEMOTE_SELF");
  }

  @Test
  void invalidRequestsAreRejected() throws Exception {
    String admin = bearer(login("admin@example.com", "admin-password-123"));

    assertThat(changeRoles(admin, learnerId, "[]")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(changeRoles(admin, UUID.randomUUID(), "[\"LEARNER\"]"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(changeRoles(admin, learnerId, "[\"SUPERUSER\"]")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anAdminCanReadAUser() throws Exception {
    String admin = bearer(login("admin@example.com", "admin-password-123"));

    assertThat(mvc.get().uri("/api/v1/admin/users/{id}", learnerId).header("Authorization", admin))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.email")
        .isEqualTo("learner@example.com");
  }
}
