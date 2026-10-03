package com.masternova.api.catalog.web;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.CategoryRepository;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The wizard's content writes and optimistic concurrency (ADR-0010), over HTTP and Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourseAuthoringIT {

  @Autowired MockMvcTester mvc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired DataSource dataSource;

  Instructor asha;
  Instructor ravi;

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

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private UUID ashasCourse() {
    return courses
        .save(aCourse().by(asha).in(categories.findBySlug("ci-cd").orElseThrow()).build())
        .id();
  }

  private MvcTestResult putDetails(UUID id, long expectedVersion, String title, UUID who) {
    return mvc.put()
        .uri("/api/v1/instructor/courses/{id}/details", id)
        .with(as(who, Role.INSTRUCTOR))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"expectedVersion":%d,"title":"%s","subtitle":"Hands-on","description":"Pods and services.",
             "categorySlug":"containers-kubernetes","level":"INTERMEDIATE","language":"en"}
            """
                .formatted(expectedVersion, title))
        .exchange();
  }

  @Test
  void anInstructorStartsADraftWhosePriceIsNotYetDecided() {
    MvcTestResult created =
        mvc.post()
            .uri("/api/v1/instructor/courses")
            .with(as(asha.id(), Role.INSTRUCTOR))
            .header("Idempotency-Key", "create-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"title":"Kubernetes: From Zero!","categorySlug":"ci-cd","level":"BEGINNER","language":"en"}
                """)
            .exchange();

    assertThat(created)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title":"Kubernetes: From Zero!","status":"DRAFT","instructorName":"Asha Rao",
             "priceMinor":0,"priceSet":false,"version":0,"sections":[]}
            """);
    assertThat(created.getResponse().getHeader("Location"))
        .startsWith("/api/v1/instructor/courses/");
    assertThat(courses.findAll())
        .singleElement()
        .satisfies(c -> assertThat(c.slug()).startsWith("kubernetes-from-zero-"));
  }

  @Test
  void detailsAndPricingEachMoveTheVersion() {
    UUID id = ashasCourse();

    assertThat(putDetails(id, 0, "Kubernetes in Practice", asha.id()))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title":"Kubernetes in Practice","level":"INTERMEDIATE",
             "category":{"slug":"containers-kubernetes"},"version":1}
            """);
    assertThat(
            mvc.put()
                .uri("/api/v1/instructor/courses/{id}/pricing", id)
                .with(as(asha.id(), Role.INSTRUCTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"priceMinor\":149900}"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"priceMinor":149900,"priceSet":true,"version":2}
            """);
  }

  /** ⭐ A stale tab: it saved from version 0 after another save made it 1. */
  @Test
  void aStaleVersionIsAConflictThatNamesBothVersions() {
    UUID id = ashasCourse();
    putDetails(id, 0, "First tab", asha.id()).assertThat().hasStatusOk();

    assertThat(putDetails(id, 0, "Second tab", asha.id()))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"VERSION_CONFLICT","expectedVersion":0,"currentVersion":1}
            """);
    assertThat(courses.findById(id).orElseThrow().title()).isEqualTo("First tab");
  }

  /**
   * ⭐ Ten tabs save the SAME version at the same moment: they all pass the pre-check, and
   * {@code @Version}'s {@code UPDATE … WHERE version = 0} lets exactly one through.
   */
  @Test
  void tenConcurrentSavesOfOneVersionHaveExactlyOneWinner() throws Exception {
    UUID id = ashasCourse();
    int writers = 10;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MvcTestResult>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(writers)) {
      for (int i = 0; i < writers; i++) {
        String title = "Tab " + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return putDetails(id, 0, title, asha.id());
                }));
      }
      start.countDown();
    }

    List<Integer> statuses = new ArrayList<>();
    String winner = null;
    for (Future<MvcTestResult> result : results) {
      MvcTestResult r = result.get();
      statuses.add(r.getResponse().getStatus());
      if (r.getResponse().getStatus() == 200) {
        winner = r.getResponse().getContentAsString();
      }
    }
    assertThat(statuses).containsOnly(200, 409);
    assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
    var saved = courses.findById(id).orElseThrow();
    assertThat(saved.version()).isEqualTo(1);
    assertThat(winner)
        .contains("\"title\":\"" + saved.title() + "\""); // the winner's title is stored
  }

  @Test
  void anArchivedCourseIsReadOnlyAndOthersCoursesAreInvisible() {
    UUID id = ashasCourse();
    mvc.post()
        .uri("/api/v1/instructor/courses/{id}/archive", id)
        .with(as(asha.id(), Role.INSTRUCTOR))
        .exchange()
        .assertThat()
        .hasStatusOk();

    assertThat(putDetails(id, 1, "Too late", asha.id()))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COURSE_ARCHIVED");
    assertThat(putDetails(id, 1, "Not mine", ravi.id())).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void inputIsValidatedAndCreatingNeedsAKey() {
    UUID id = ashasCourse();

    assertThat(putDetails(id, 0, "", asha.id()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("title");
    assertThat(
            mvc.post()
                .uri("/api/v1/instructor/courses")
                .with(as(asha.id(), Role.INSTRUCTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"X\",\"categorySlug\":\"nope\",\"level\":\"BEGINNER\",\"language\":\"en\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST); // no Idempotency-Key
  }
}
