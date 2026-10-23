package com.masternova.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.application.SignupService;
import com.masternova.api.identity.application.SignupService.SignupCommand;
import com.masternova.api.identity.domain.UserRepository;
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

/** Becoming an instructor: apply (at signup or later), an admin decides, the role follows. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InstructorApplicationIT {

  @Autowired MockMvcTester mvc;
  @Autowired SignupService signup;
  @Autowired UserRepository users;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private String admin;

  @BeforeEach
  void anAdminExists() throws Exception {
    jdbc.sql("DELETE FROM app_user").update();
    UUID adminId =
        signup.signup(new SignupCommand("admin@example.com", "Ada", "admin-password-123")).id();
    jdbc.sql("INSERT INTO app_user_role (user_id, role) VALUES (:id, 'ADMIN')")
        .param("id", adminId)
        .update();
    admin = bearer("admin@example.com", "admin-password-123");
  }

  private String bearer(String email, String password) throws Exception {
    MvcTestResult login =
        mvc.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
            .exchange();
    return "Bearer "
        + json.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
  }

  private MvcTestResult signupViaApi(String email, boolean applyAsInstructor) {
    return mvc.post()
        .uri("/api/v1/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email":"%s","displayName":"Ina","password":"teacher-password-1","applyAsInstructor":%s}
            """
                .formatted(email, applyAsInstructor))
        .exchange();
  }

  private MvcTestResult queue(String bearer) {
    return mvc.get()
        .uri("/api/v1/admin/instructor-applications")
        .header("Authorization", bearer)
        .exchange();
  }

  private MvcTestResult decide(UUID userId, String decision) {
    return mvc.post()
        .uri("/api/v1/admin/instructor-applications/{id}/{d}", userId, decision)
        .header("Authorization", admin)
        .exchange();
  }

  private MvcTestResult apply(String bearer, String pitch) {
    return mvc.post()
        .uri("/api/v1/me/instructor-application")
        .header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(java.util.Map.of("pitch", pitch)))
        .exchange();
  }

  private UUID idOf(String email) {
    return users.findByEmail(email).orElseThrow().id();
  }

  @Test
  void signingUpAsAnInstructorFilesAnApplicationButGrantsNothing() throws Exception {
    assertThat(signupViaApi("ina@example.com", true))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.roles")
        .asArray()
        .containsExactly("LEARNER"); // no one chooses a role

    assertThat(queue(admin))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"items":[{"email":"ina@example.com","displayName":"Ina","emailVerified":false}],
             "nextCursor":null}
            """);
    assertThat(signupViaApi("leo@example.com", false)).hasStatus(HttpStatus.CREATED);
    assertThat(queue(admin)).bodyJson().extractingPath("$.items").asArray().hasSize(1);
  }

  @Test
  void approvingGrantsTheRoleOnceEvenWhenClickedTwice() throws Exception {
    signupViaApi("ina@example.com", true);
    UUID ina = idOf("ina@example.com");

    assertThat(decide(ina, "approve"))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("APPROVED");
    assertThat(decide(ina, "approve")).hasStatus(HttpStatus.OK); // idempotent
    assertThat(users.findById(ina).orElseThrow().roles())
        .containsExactlyInAnyOrder(Role.LEARNER, Role.INSTRUCTOR);
    assertThat(queue(admin)).bodyJson().extractingPath("$.items").asArray().isEmpty();
    assertThat(decide(ina, "reject"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("APPLICATION_DECIDED");
  }

  @Test
  void aLearnerAppliesLaterAndReappliesAfterARejection() throws Exception {
    signupViaApi("leo@example.com", false);
    String leo = bearer("leo@example.com", "teacher-password-1");
    UUID leoId = idOf("leo@example.com");

    assertThat(mvc.get().uri("/api/v1/me/instructor-application").header("Authorization", leo))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(apply(leo, "Spring Boot for beginners"))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PENDING");
    assertThat(apply(leo, "again"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("APPLICATION_PENDING");

    decide(leoId, "reject");
    assertThat(mvc.get().uri("/api/v1/me/instructor-application").header("Authorization", leo))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("REJECTED");
    assertThat(apply(leo, "With an outline this time"))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PENDING");
  }

  @Test
  void anApprovedInstructorCannotReapplyEvenWithAStaleLearnerToken() throws Exception {
    signupViaApi("ina@example.com", true);
    String staleToken = bearer("ina@example.com", "teacher-password-1"); // says LEARNER
    decide(idOf("ina@example.com"), "approve");

    // ⭐ the rule reads the stored roles, not the 15-minute-old token
    assertThat(apply(staleToken, "again"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ALREADY_INSTRUCTOR");
  }

  @Test
  void onlyAdminsSeeTheQueueOrDecide() throws Exception {
    signupViaApi("ina@example.com", true);
    String ina = bearer("ina@example.com", "teacher-password-1");

    assertThat(queue(ina)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/instructor-applications/{id}/approve", idOf("ina@example.com"))
                .header("Authorization", ina))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(users.findById(idOf("ina@example.com")).orElseThrow().roles())
        .containsExactly(Role.LEARNER);
  }

  @Test
  void anAdminFindsAUserByEmailNotByUuid() throws Exception {
    signupViaApi("ina@example.com", false);

    assertThat(
            mvc.get()
                .uri("/api/v1/admin/users?email={e}", "  INA@example.com ")
                .header("Authorization", admin))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.displayName")
        .isEqualTo("Ina");
    assertThat(
            mvc.get()
                .uri("/api/v1/admin/users?email={e}", "nobody@example.com")
                .header("Authorization", admin))
        .hasStatus(HttpStatus.NOT_FOUND);
  }
}
