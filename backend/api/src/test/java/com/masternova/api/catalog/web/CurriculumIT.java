package com.masternova.api.catalog.web;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.identity.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Curriculum commands through HTTP and REAL Postgres: the parts an in-memory test can't prove —
 * Hibernate re-parenting a lecture under orphanRemoval, renumbering under the deferred unique
 * constraints, and the version serialising concurrent edits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CurriculumIT {

  @Autowired MockMvcTester mvc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactions;

  Instructor asha;
  UUID courseId;

  @BeforeEach
  void aCourseWithTwoSections() {
    clean();
    asha = new TestInstructors(dataSource).create("Asha Rao");
    courseId =
        courses
            .save(
                aCourse()
                    .by(asha)
                    .in(categories.findBySlug("ci-cd").orElseThrow())
                    .withSection("Intro", aLecture("Welcome").preview(), aLecture("Setup"))
                    .withSection("Core", aLecture("Pods").seconds(600), aLecture("Services"))
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

  private MvcTestResult command(long expectedVersion, String commandJson) {
    return mvc.post()
        .uri("/api/v1/instructor/courses/{id}/curriculum", courseId)
        .with(as(asha.id(), Role.INSTRUCTOR))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"expectedVersion\":%d,\"command\":%s}".formatted(expectedVersion, commandJson))
        .exchange();
  }

  /** The curriculum as stored, read with plain SQL (not through the entities under test). */
  private List<String> stored() {
    return JdbcClient.create(dataSource)
        .sql(
            """
            SELECT s.position || ':' || s.title || '/' || l.position || ':' || l.title
              FROM section s LEFT JOIN lecture l ON l.section_id = s.id
             WHERE s.course_id = :id ORDER BY s.position, l.position
            """)
        .param("id", courseId)
        .query(String.class)
        .list();
  }

  private Course load() {
    return new TransactionTemplate(transactions)
        .execute(
            s -> {
              Course c = courses.findWithCurriculumById(courseId).orElseThrow();
              c.sections().forEach(sec -> sec.lectures().size());
              return c;
            });
  }

  @Test
  void addingASectionAndALectureReturnsTheNewCurriculumAndVersion() {
    assertThat(command(0, "{\"kind\":\"ADD_SECTION\",\"title\":\"Bonus\"}"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"version":1,"lectureCount":4,"sections":[{"title":"Intro"},{"title":"Core"},{"title":"Bonus","lectures":[]}]}
            """);
    UUID bonus = load().sections().get(2).id();

    // the lecture's own kind is "lectureKind": "kind" is the command's type
    assertThat(
            command(
                1,
                "{\"kind\":\"ADD_LECTURE\",\"sectionId\":\"%s\",\"title\":\"Q&A\",\"lectureKind\":\"ARTICLE\",\"durationSeconds\":120}"
                    .formatted(bonus)))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"version":2,"lectureCount":5,"sections":[{},{},{"lectures":[{"title":"Q&A","kind":"ARTICLE"}]}]}
            """);
  }

  /** ⭐ Re-parenting under orphanRemoval: the lecture must MOVE, not be deleted. */
  @Test
  void aLectureMovesToAnotherSectionAndKeepsItsId() {
    Course before = load();
    UUID pods = before.sections().get(1).lectures().get(0).id();
    UUID intro = before.sections().get(0).id();

    command(
            0,
            "{\"kind\":\"MOVE_LECTURE\",\"lectureId\":\"%s\",\"toSectionId\":\"%s\",\"toPosition\":0}"
                .formatted(pods, intro))
        .assertThat()
        .hasStatusOk();

    assertThat(stored())
        .containsExactly(
            "0:Intro/0:Pods", "0:Intro/1:Welcome", "0:Intro/2:Setup", "1:Core/0:Services");
    assertThat(load().sections().get(0).lectures().get(0).id()).isEqualTo(pods);
  }

  /** ⭐ A full reversal: every row changes position, legal only once ALL have moved (deferred). */
  @Test
  void sectionsReorderWithoutTrippingTheUniquePosition() {
    Course before = load();
    String order =
        "[\"%s\",\"%s\"]".formatted(before.sections().get(1).id(), before.sections().get(0).id());

    command(0, "{\"kind\":\"REORDER_SECTIONS\",\"order\":" + order + "}")
        .assertThat()
        .hasStatusOk();

    assertThat(stored()).first().isEqualTo("0:Core/0:Pods");
  }

  @Test
  void removingASectionDeletesItsLecturesAndKeepsTheRollupsTrue() {
    UUID core = load().sections().get(1).id();

    assertThat(command(0, "{\"kind\":\"REMOVE_SECTION\",\"sectionId\":\"%s\"}".formatted(core)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.lectureCount")
        .isEqualTo(2);
    assertThat(stored()).containsExactly("0:Intro/0:Welcome", "0:Intro/1:Setup");
  }

  @Test
  void clientsCantSendRestoreCommandsOrStaleVersions() {
    assertThat(
            command(
                0,
                "{\"kind\":\"RESTORE_LECTURE\",\"sectionId\":\"%s\",\"position\":0,\"snapshot\":{\"id\":\"%s\",\"title\":\"x\",\"kind\":\"VIDEO\",\"preview\":false,\"durationSeconds\":1,\"assetId\":\"%s\"}}"
                    .formatted(
                        load().sections().get(0).id(), UUID.randomUUID(), UUID.randomUUID())))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].code")
        .isEqualTo("NOT_A_CLIENT_COMMAND");
    assertThat(command(7, "{\"kind\":\"ADD_SECTION\",\"title\":\"Late\"}"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"VERSION_CONFLICT","expectedVersion":7,"currentVersion":0}
            """);
    assertThat(command(0, "{\"kind\":\"DROP_TABLE\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            command(
                0,
                "{\"kind\":\"RENAME_SECTION\",\"sectionId\":\"%s\",\"title\":\"x\"}"
                    .formatted(UUID.randomUUID())))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  /** ⭐ Ten editors, one version: exactly one edit lands; nine get 409; one section is added. */
  @Test
  void concurrentEditsOfOneVersionAreSerialisedByTheCourse() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MvcTestResult>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
      for (int i = 0; i < 10; i++) {
        String title = "Parallel " + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return command(0, "{\"kind\":\"ADD_SECTION\",\"title\":\"" + title + "\"}");
                }));
      }
      start.countDown();
    }
    List<Integer> statuses = new ArrayList<>();
    for (Future<MvcTestResult> r : results) {
      statuses.add(r.get().getResponse().getStatus());
    }

    assertThat(statuses).containsOnly(200, 409).filteredOn(s -> s == 200).hasSize(1);
    assertThat(load().sections()).hasSize(3); // the losers' inserts rolled back with them
  }

  /** ⭐ The gate re-runs on approval: a course gutted while waiting in review can't be published. */
  @Test
  void aCourseGuttedWhileInReviewIsRefusedOnPublish() {
    mvc.post()
        .uri("/api/v1/instructor/courses/{id}/submit", courseId)
        .with(as(asha.id(), Role.INSTRUCTOR))
        .exchange()
        .assertThat()
        .hasStatusOk(); // version 1, IN_REVIEW
    for (UUID section : load().sections().stream().map(s -> s.id()).toList()) {
      long version = load().version();
      command(version, "{\"kind\":\"REMOVE_SECTION\",\"sectionId\":\"%s\"}".formatted(section))
          .assertThat()
          .hasStatusOk();
    }

    assertThat(
            mvc.post()
                .uri("/api/v1/admin/courses/{id}/publish", courseId)
                .with(as(UUID.randomUUID(), Role.ADMIN)))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.problems[*].code")
        .asArray()
        .contains("NO_SECTIONS");
  }
}
