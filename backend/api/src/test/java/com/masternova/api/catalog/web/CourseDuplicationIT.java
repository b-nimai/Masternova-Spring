package com.masternova.api.catalog.web;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.identity.Role;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Duplicate through HTTP: one copy per Idempotency-Key, in one transaction, for the right people.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourseDuplicationIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;

  @Autowired DataSource dataSource;

  UUID asha;
  UUID ravi;
  UUID sourceId;

  @BeforeEach
  void aPublishedCourseWithACurriculum() {
    clean();
    TestInstructors instructors = new TestInstructors(dataSource);
    Instructor ashaRao = instructors.create("Asha Rao");
    asha = ashaRao.id();
    ravi = instructors.create("Ravi Kumar").id();
    sourceId =
        courses
            .save(
                aCourse()
                    .slug("k8s")
                    .title("Kubernetes")
                    .by(ashaRao)
                    .in(categories.findBySlug("containers-kubernetes").orElseThrow())
                    .priced(149900)
                    .withSection(
                        "Intro",
                        aLecture("Welcome").preview().seconds(90).asset(UUID.randomUUID()),
                        aLecture("Setup").article(),
                        aLecture("Pods").seconds(600))
                    .published()
                    .build())
            .id();
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private MvcTestResult duplicate(RequestPostProcessor who, String key) {
    var request = mvc.post().uri("/api/v1/instructor/courses/{id}/duplicate", sourceId).with(who);
    if (key != null) {
      request = request.header("Idempotency-Key", key);
    }
    return request.exchange();
  }

  private long courseCount() {
    return jdbc.sql("SELECT count(*) FROM course").query(Long.class).single();
  }

  @Test
  void theOwnerGetsADraftCopyWithTheWholeCurriculum() {
    MvcTestResult result = duplicate(as(asha, Role.INSTRUCTOR), "dup-1");

    assertThat(result)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title":"Kubernetes (copy)","status":"DRAFT","publishedAt":null,"lectureCount":3,
             "instructorName":"Asha Rao","sections":[{"title":"Intro","lectures":[{"title":"Welcome"},{"title":"Setup"},{"title":"Pods"}]}]}
            """);
    String location = result.getResponse().getHeader("Location");
    assertThat(location).matches("/api/v1/courses/k8s-copy-[a-z0-9]{6}");

    // persisted: a draft, its sections and lectures all in the database (one transaction)
    assertThat(courseCount()).isEqualTo(2);
    assertThat(jdbc.sql("SELECT count(*) FROM lecture").query(Long.class).single()).isEqualTo(6);
    assertThat(mvc.get().uri(location).with(as(asha, Role.INSTRUCTOR))).hasStatusOk();
    assertThat(mvc.get().uri(location)).hasStatus(HttpStatus.NOT_FOUND); // a draft: not public
  }

  /** ⭐ A double-clicked button: same key twice → the stored 201 again, and ONE copy. */
  @Test
  void theSameIdempotencyKeyMakesOneCopy() {
    MvcTestResult first = duplicate(as(asha, Role.INSTRUCTOR), "dup-twice");
    MvcTestResult retry = duplicate(as(asha, Role.INSTRUCTOR), "dup-twice");

    assertThat(retry).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "true");
    assertThat(retry.getResponse().getHeader("Location"))
        .isEqualTo(first.getResponse().getHeader("Location"));
    assertThat(courseCount()).isEqualTo(2); // source + exactly one copy

    duplicate(as(asha, Role.INSTRUCTOR), "dup-another"); // a NEW key is a new intent
    assertThat(courseCount()).isEqualTo(3);
  }

  @Test
  void theKeyIsRequired() {
    assertThat(duplicate(as(asha, Role.INSTRUCTOR), null))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("Idempotency-Key");
    assertThat(courseCount()).isEqualTo(1);
  }

  @Test
  void onlyTheOwnerOrAnAdminMayDuplicate() {
    assertThat(duplicate(as(ravi, Role.INSTRUCTOR), "k1")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(duplicate(as(ravi, Role.LEARNER), "k2")).hasStatus(HttpStatus.FORBIDDEN);

    // an admin duplicates on the instructor's behalf: the copy stays Asha's
    assertThat(duplicate(as(UUID.randomUUID(), Role.ADMIN), "k3"))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.instructorName")
        .isEqualTo("Asha Rao");
    assertThat(courseCount()).isEqualTo(2);
  }
}
