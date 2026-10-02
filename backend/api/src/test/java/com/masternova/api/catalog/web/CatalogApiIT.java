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
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The catalog's read API through real HTTP (MockMvc), the real security chain and Postgres: keyset
 * paging correctness (ADR-0009), the query-string contract, visibility, and the role rule.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiIT {

  static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

  @Autowired MockMvcTester mvc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired JsonMapper json;
  @Autowired EntityManagerFactory emf;

  @Autowired DataSource dataSource;

  Instructor asha;
  Instructor ravi;

  @BeforeEach
  void seedInstructors() {
    clean();
    TestInstructors instructors = new TestInstructors(dataSource);
    asha = instructors.create("Asha Rao");
    ravi = instructors.create("Ravi Kumar");
  }

  /** Courses reference users (ON DELETE RESTRICT): leave nothing behind for other test classes. */
  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  // ------------------------------------------------------------------ helpers

  /** A course in {@code category}; {@code publishedAt == null} keeps it a draft. */
  private Course course(
      String slug, Instructor instructor, String category, long price, Instant publishedAt) {
    return aCourse()
        .slug(slug)
        .by(instructor)
        .in(categories.findBySlug(category).orElseThrow())
        .priced(price)
        .published(publishedAt)
        .build();
  }

  private void save(Course... all) {
    courses.saveAll(List.of(all));
  }

  private JsonNode get(String uri, RequestPostProcessor... auth) throws Exception {
    var request = mvc.get().uri(uri);
    for (RequestPostProcessor a : auth) {
      request = request.with(a);
    }
    MvcTestResult result = request.exchange();
    assertThat(result).hasStatusOk();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private static List<String> slugs(JsonNode page) {
    List<String> slugs = new ArrayList<>();
    page.get("items").forEach(item -> slugs.add(item.get("slug").asString()));
    return slugs;
  }

  private static String next(JsonNode page) {
    JsonNode cursor = page.get("nextCursor");
    return cursor == null || cursor.isNull() ? null : cursor.asString();
  }

  private static RequestPostProcessor as(UUID id, Role... roles) {
    return jwt()
        .jwt(
            j ->
                j.subject(id.toString())
                    .claim("roles", Arrays.stream(roles).map(Enum::name).toList()))
        .authorities(
            Arrays.stream(roles)
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority(r.authority()))
                .toList());
  }

  // ------------------------------------------------------------------ keyset paging

  /** ⭐ The test that catches a missing tiebreaker: 55 rows share ONE sort key. */
  @Test
  void pagesWithNoGapsAndNoDuplicatesWhenEveryRowSharesTheSortKey() throws Exception {
    List<Course> all = new ArrayList<>();
    for (int i = 0; i < 55; i++) {
      all.add(course("same-" + i, asha, "ci-cd", 0, T0));
    }
    courses.saveAll(all);

    List<Integer> sizes = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String cursor = null;
    do {
      JsonNode page = get("/api/v1/courses?limit=20" + (cursor == null ? "" : "&cursor=" + cursor));
      sizes.add(page.get("items").size());
      for (String slug : slugs(page)) {
        assertThat(seen.add(slug)).as("%s appeared twice", slug).isTrue();
      }
      cursor = next(page);
    } while (cursor != null);

    assertThat(sizes).containsExactly(20, 20, 15);
    assertThat(seen).hasSize(55);
  }

  /** ⭐ The OFFSET bug, and why keyset doesn't have it (ADR-0009, problem 2). */
  @Test
  void aCoursePublishedWhileReadingDoesNotShiftThePages() throws Exception {
    for (int i = 0; i < 10; i++) {
      save(course("c" + i, asha, "ci-cd", 0, T0.plusSeconds(60L * i)));
    }
    JsonNode first = get("/api/v1/courses?sort=NEWEST&limit=4");
    assertThat(slugs(first)).containsExactly("c9", "c8", "c7", "c6");

    save(course("brand-new", ravi, "ci-cd", 0, T0.plusSeconds(3600))); // lands on top of page 1

    // with OFFSET 4 the second page would start with c6 again; the cursor continues after c6
    JsonNode second = get("/api/v1/courses?sort=NEWEST&limit=4&cursor=" + next(first));
    assertThat(slugs(second)).containsExactly("c5", "c4", "c3", "c2");
  }

  @Test
  void priceSortsBreakTiesByIdAndStillCoverEveryRow() throws Exception {
    long[] prices = {99900, 49900, 0, 49900, 49900};
    for (int i = 0; i < prices.length; i++) {
      save(course("p" + i, asha, "ci-cd", prices[i], T0));
    }

    List<Long> ascending = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode page =
          get(
              "/api/v1/courses?sort=PRICE_LOW&limit=2"
                  + (cursor == null ? "" : "&cursor=" + cursor));
      page.get("items").forEach(c -> ascending.add(c.get("priceMinor").asLong()));
      cursor = next(page);
    } while (cursor != null);

    assertThat(ascending).containsExactly(0L, 49900L, 49900L, 49900L, 99900L);
    assertThat(slugs(get("/api/v1/courses?sort=PRICE_HIGH&limit=1"))).containsExactly("p0");
  }

  @Test
  void highestRatedComesFirst() throws Exception {
    var cicd = categories.findBySlug("ci-cd").orElseThrow();
    save(
        aCourse().slug("good").by(asha).in(cicd).rated("4.20", 5).published(T0).build(),
        aCourse().slug("best").by(asha).in(cicd).rated("4.90", 9).published(T0).build(),
        course("unrated", asha, "ci-cd", 0, T0));

    assertThat(slugs(get("/api/v1/courses?sort=HIGHEST_RATED")))
        .containsExactly("best", "good", "unrated");
  }

  /** ⭐ A list page is ONE statement: the keyset query fetches the category in the same join. */
  @Test
  void aPageOfCoursesIsOneStatement() throws Exception {
    String[] cats = {"ci-cd", "ui-ux", "marketing", "data-science", "mobile-development"};
    for (int i = 0; i < 20; i++) {
      save(course("s" + i, asha, cats[i % cats.length], 0, T0.plusSeconds(i)));
    }
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    JsonNode page = get("/api/v1/courses?limit=20");

    assertThat(page.get("items").size()).isEqualTo(20);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
  }

  // ------------------------------------------------------------------ the query-string contract

  @Test
  void facetsBindFromTheQueryString() throws Exception {
    Course k8s = course("k8s", asha, "containers-kubernetes", 0, T0);
    Course pricey = course("pricey", asha, "containers-kubernetes", 99900, T0.plusSeconds(1));
    save(k8s, pricey, course("design", ravi, "ui-ux", 0, T0.plusSeconds(2)));

    assertThat(slugs(get("/api/v1/courses?category=devops-cloud&price=FREE")))
        .containsExactly("k8s");
    // ⭐ a template variable gets URL-encoded properly ("Course K" → Course%20K); a literal "%20"
    //    in the string would be encoded AGAIN, to %2520
    assertThat(
            mvc.get()
                .uri("/api/v1/courses?level=BEGINNER&level=ADVANCED&q={q}", "COURSE K")
                .exchange())
        .bodyJson()
        .extractingPath("$.items[*].slug")
        .asArray()
        .containsExactly("k8s");
    assertThat(slugs(get("/api/v1/courses?category=no-such-category"))).isEmpty();
  }

  @Test
  void aCursorBelongsToItsSort() throws Exception {
    save(course("a", asha, "ci-cd", 0, T0), course("b", asha, "ci-cd", 0, T0.plusSeconds(1)));
    String newest = next(get("/api/v1/courses?sort=NEWEST&limit=1"));

    assertThat(mvc.get().uri("/api/v1/courses?sort=PRICE_LOW&cursor=" + newest))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"VALIDATION_FAILED","errors":[{"field":"cursor","code":"INVALID_CURSOR"}]}
            """);
    assertThat(mvc.get().uri("/api/v1/courses?cursor=not-a-cursor"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theQueryStringIsValidated() {
    assertThat(mvc.get().uri("/api/v1/courses?limit=0"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("limit");
    assertThat(mvc.get().uri("/api/v1/courses?minRating=6"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("minRating");
    assertThat(mvc.get().uri("/api/v1/courses?sort=BEST"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("sort");
    assertThat(mvc.get().uri("/api/v1/courses?sort=RECENTLY_UPDATED"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].code")
        .isEqualTo("UNSUPPORTED_SORT");
  }

  // ------------------------------------------------------------------ the course page

  @Test
  void theCoursePageShowsTheCurriculumInOrderWithMoneyInMinorUnits() throws Exception {
    save(
        aCourse()
            .slug("k8s")
            .by(asha)
            .in(categories.findBySlug("containers-kubernetes").orElseThrow())
            .priced(149900)
            .withSection("Intro", aLecture("Welcome").preview().seconds(90))
            .withSection("Core", aLecture("Pods").seconds(600), aLecture("Services").article())
            .published(T0)
            .build());

    assertThat(mvc.get().uri("/api/v1/courses/k8s"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"slug":"k8s","priceMinor":149900,"currency":"INR","status":"PUBLISHED",
             "lectureCount":3,"totalDurationSeconds":690,"instructorName":"Asha Rao",
             "category":{"slug":"containers-kubernetes","name":"Containers & Kubernetes"},
             "sections":[
               {"title":"Intro","lectures":[{"title":"Welcome","kind":"VIDEO","preview":true,"durationSeconds":90}]},
               {"title":"Core","lectures":[{"title":"Pods"},{"title":"Services","kind":"ARTICLE"}]}]}
            """);
  }

  /** ⭐ 404, never 403: a 403 would confirm that the draft exists. */
  @Test
  void aDraftIsA404ForStrangersAndVisibleToItsOwnerAndAdmins() {
    save(course("secret-draft", asha, "ci-cd", 0, null));
    String uri = "/api/v1/courses/secret-draft";

    assertThat(mvc.get().uri(uri))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("NOT_FOUND");
    assertThat(mvc.get().uri(uri).with(as(ravi.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri(uri).with(as(asha.id(), Role.INSTRUCTOR))).hasStatusOk();
    assertThat(mvc.get().uri(uri).with(as(UUID.randomUUID(), Role.ADMIN))).hasStatusOk();
    assertThat(mvc.get().uri("/api/v1/courses/no-such-course")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void draftsNeverAppearInThePublicList() throws Exception {
    save(course("live", asha, "ci-cd", 0, T0), course("draft", asha, "ci-cd", 0, null));

    // not even for their owner: the public list is the storefront; the owner has /instructor
    assertThat(slugs(get("/api/v1/courses", as(asha.id(), Role.INSTRUCTOR))))
        .containsExactly("live");
  }

  @Test
  void categoriesComeAsATwoLevelTree() throws Exception {
    JsonNode tree = get("/api/v1/categories");

    assertThat(tree.size()).isEqualTo(5);
    assertThat(tree.get(1).get("slug").asString()).isEqualTo("devops-cloud");
    assertThat(tree.get(1).get("children").get(0).get("slug").asString())
        .isEqualTo("containers-kubernetes");
  }

  // ------------------------------------------------------------------ the instructor's list

  @Test
  void instructorsListTheirOwnCoursesInEveryState() throws Exception {
    save(
        course("mine-live", asha, "ci-cd", 0, T0),
        course("mine-draft", asha, "ci-cd", 0, null),
        course("ravis", ravi, "ci-cd", 0, T0));

    assertThat(slugs(get("/api/v1/instructor/courses", as(asha.id(), Role.INSTRUCTOR))))
        .containsExactlyInAnyOrder("mine-live", "mine-draft");
    assertThat(mvc.get().uri("/api/v1/instructor/courses").with(as(asha.id(), Role.LEARNER)))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/v1/instructor/courses")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.get()
                .uri("/api/v1/instructor/courses?limit=500")
                .with(as(asha.id(), Role.INSTRUCTOR)))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }
}
