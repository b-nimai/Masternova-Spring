package com.masternova.api.catalog.web;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseAction;
import com.masternova.api.catalog.domain.CourseBuilder;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.identity.Role;
import java.time.Instant;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The lifecycle through HTTP, the security chain and Postgres (docs/lld/catalog-authoring.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourseLifecycleIT {

  @Autowired MockMvcTester mvc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactions;

  Instructor asha;
  Instructor ravi;
  final UUID adminId = UUID.randomUUID();

  @BeforeEach
  void instructors() {
    clean();
    TestInstructors instructors = new TestInstructors(dataSource);
    asha = instructors.create("Asha Rao");
    ravi = instructors.create("Ravi Kumar");
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  private CourseBuilder ashas(String slug) {
    return aCourse().slug(slug).by(asha).in(categories.findBySlug("ci-cd").orElseThrow());
  }

  private UUID save(Course course) {
    return courses.save(course).id();
  }

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private MockMvcTester.MockMvcRequestBuilder post(String uri, UUID id, RequestPostProcessor who) {
    return mvc.post().uri(uri, id).with(who);
  }

  @Test
  void anIncompleteCourseIsRefusedWithEveryMissingStep() {
    UUID id = save(ashas("unfinished").description("Too short.").unpriced().build());

    assertThat(
            mvc.get()
                .uri("/api/v1/instructor/courses/{id}/readiness", id)
                .with(as(asha.id(), Role.INSTRUCTOR)))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"ready":false,"requirements":[
              {"code":"DESCRIPTION_TOO_SHORT","satisfied":false},{"code":"PRICE_NOT_SET","satisfied":false},
              {"code":"NO_SECTIONS","satisfied":false},{"code":"EMPTY_SECTION","satisfied":true},
              {"code":"TOO_FEW_LECTURES","satisfied":false},{"code":"NO_PREVIEW","satisfied":false}]}
            """);

    assertThat(post("/api/v1/instructor/courses/{id}/submit", id, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"COURSE_NOT_READY","problems":[{"code":"DESCRIPTION_TOO_SHORT"},{"code":"PRICE_NOT_SET"},
             {"code":"NO_SECTIONS"},{"code":"TOO_FEW_LECTURES"},{"code":"NO_PREVIEW"}]}
            """);
  }

  /**
   * ⭐ The whole path: submit (owner) → publish (reviewer only) → public; each step a new version.
   */
  @Test
  void anOwnerSubmitsAndOnlyAReviewerPublishes() {
    UUID readyId = save(ashas("ready-2").submitted().build()); // stored IN_REVIEW, version 0

    assertThat(post("/api/v1/admin/courses/{id}/publish", readyId, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.FORBIDDEN); // an instructor can't approve their own course
    assertThat(
            post(
                "/api/v1/instructor/courses/{id}/publish", readyId, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.NOT_FOUND); // there is no author route for publishing

    assertThat(post("/api/v1/admin/courses/{id}/publish", readyId, as(adminId, Role.ADMIN)))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status":"PUBLISHED","version":1}
            """); // ⭐ the transition bumped the version: open editor tabs are now stale
    assertThat(mvc.get().uri("/api/v1/courses/ready-2")).hasStatusOk(); // in the storefront now
  }

  @Test
  void aDraftSubmitsAndCanBeWithdrawn() {
    UUID id =
        save(
            ashas("draft")
                .withSection(
                    "Intro",
                    CourseBuilder.aLecture("A").preview(),
                    CourseBuilder.aLecture("B"),
                    CourseBuilder.aLecture("C"))
                .build());

    assertThat(post("/api/v1/instructor/courses/{id}/submit", id, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("IN_REVIEW");
    assertThat(post("/api/v1/instructor/courses/{id}/withdraw", id, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("DRAFT");
  }

  @Test
  void illegalTransitionsAreConflictsThatNameTheEdge() {
    UUID draft = save(ashas("draft").build());

    assertThat(post("/api/v1/admin/courses/{id}/publish", draft, as(adminId, Role.ADMIN)))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"ILLEGAL_TRANSITION","from":"DRAFT","action":"PUBLISH"}
            """); // ⭐ review is not optional
  }

  @Test
  void archivingIsTerminalAndHidesTheCourse() {
    UUID id = save(ashas("old").published().build());

    assertThat(post("/api/v1/instructor/courses/{id}/archive", id, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatusOk();
    assertThat(mvc.get().uri("/api/v1/courses/old")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(post("/api/v1/instructor/courses/{id}/submit", id, as(asha.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ILLEGAL_TRANSITION");
  }

  @Test
  void someoneElsesCourseIsA404AndLearnersAre403() {
    UUID id = save(ashas("mine").build());

    assertThat(post("/api/v1/instructor/courses/{id}/archive", id, as(ravi.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(post("/api/v1/instructor/courses/{id}/archive", id, as(asha.id(), Role.LEARNER)))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  /**
   * ⭐ Two transitions each legal from what its caller READ: the second one to commit is working on
   * a stale copy. {@code @Version} turns it into an optimistic-lock failure (→ 409) instead of
   * letting it overwrite the first one's result.
   */
  @Test
  void aTransitionBasedOnAStaleCopyIsRejectedByTheVersion() {
    UUID id = save(ashas("raced").submitted().build());
    TransactionTemplate tx = new TransactionTemplate(transactions);
    Instant now = Instant.parse("2026-10-03T10:00:00Z");

    Course stale = tx.execute(s -> courses.findById(id).orElseThrow()); // read: IN_REVIEW, v1
    tx.executeWithoutResult(
        s -> courses.findById(id).orElseThrow().transition(CourseAction.ARCHIVE, now)); // v2

    stale.transition(CourseAction.WITHDRAW, now); // legal from what it read…
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> courses.save(stale)))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class); // …but that's history now
    assertThat(courses.findById(id).orElseThrow().status().name()).isEqualTo("ARCHIVED");
  }
}
