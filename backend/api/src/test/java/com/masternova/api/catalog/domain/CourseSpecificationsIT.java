package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseSpecifications.atLevels;
import static com.masternova.api.catalog.domain.CourseSpecifications.byInstructor;
import static com.masternova.api.catalog.domain.CourseSpecifications.free;
import static com.masternova.api.catalog.domain.CourseSpecifications.inCategories;
import static com.masternova.api.catalog.domain.CourseSpecifications.inLanguage;
import static com.masternova.api.catalog.domain.CourseSpecifications.paid;
import static com.masternova.api.catalog.domain.CourseSpecifications.published;
import static com.masternova.api.catalog.domain.CourseSpecifications.ratedAtLeast;
import static com.masternova.api.catalog.domain.CourseSpecifications.titleContains;
import static com.masternova.api.catalog.domain.CourseSpecifications.visibleTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.application.CourseSearch;
import com.masternova.api.catalog.application.CourseSearch.PriceFilter;
import com.masternova.kernel.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;

/**
 * ⭐ Every Specification leaf runs against REAL Postgres over one small, varied catalog — a typo in
 * an attribute name or a wrong operator fails here, not in production. And the visibility rule's
 * two forms (SQL and in-memory) are proven to agree.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CourseSpecificationsIT {

  static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  @Autowired TestEntityManager em;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;

  UUID asha;
  UUID ravi;

  private UUID instructor(String name) {
    UUID id = UUID.randomUUID();
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO app_user (id, email, display_name, password_hash, created_at, version)"
                + " VALUES (?1, ?2, ?3, '{noop}x', now(), 0)")
        .setParameter(1, id)
        .setParameter(2, id + "@example.com")
        .setParameter(3, name)
        .executeUpdate();
    return id;
  }

  private void course(
      String slug,
      String title,
      String category,
      CourseLevel level,
      String language,
      long priceMinor,
      String rating,
      CourseStatus status,
      UUID instructor) {
    Course course =
        Course.draft(
            slug,
            title,
            "About " + title,
            level,
            language,
            Money.of(priceMinor, "INR"),
            categories.findBySlug(category).orElseThrow(),
            new Instructor(instructor, "Someone"),
            NOW);
    if (!rating.equals("0")) {
      course.updateRatingSummary(new BigDecimal(rating), 10);
    }
    if (status == CourseStatus.PUBLISHED) {
      course.publish(NOW);
    } else if (status == CourseStatus.IN_REVIEW) {
      em.persist(course);
      em.flush();
      em.getEntityManager()
          .createNativeQuery("UPDATE course SET status = 'IN_REVIEW' WHERE id = ?1")
          .setParameter(1, course.id())
          .executeUpdate(); // Phase 6 adds the real submit transition
      return;
    }
    em.persist(course);
  }

  @BeforeEach
  void seed() {
    asha = instructor("Asha");
    ravi = instructor("Ravi");
    // slug           title                   category                level   lang  price   rating
    // status   by
    course(
        "k8s-basics",
        "Kubernetes Basics",
        "containers-kubernetes",
        CourseLevel.BEGINNER,
        "en",
        0,
        "4.6",
        CourseStatus.PUBLISHED,
        asha);
    course(
        "k8s-advanced",
        "Advanced Kubernetes",
        "containers-kubernetes",
        CourseLevel.ADVANCED,
        "en",
        299900,
        "4.8",
        CourseStatus.PUBLISHED,
        asha);
    course(
        "cicd-hindi",
        "CI/CD in Hindi",
        "ci-cd",
        CourseLevel.BEGINNER,
        "hi",
        49900,
        "4.1",
        CourseStatus.PUBLISHED,
        ravi);
    course(
        "aws-draft",
        "AWS Draft",
        "cloud-platforms",
        CourseLevel.INTERMEDIATE,
        "en",
        99900,
        "0",
        CourseStatus.DRAFT,
        asha);
    course(
        "react-100",
        "React 100% Practical",
        "web-development",
        CourseLevel.BEGINNER,
        "en",
        0,
        "3.9",
        CourseStatus.PUBLISHED,
        ravi);
    course(
        "figma-review",
        "Figma for 100 days",
        "ui-ux",
        CourseLevel.ALL_LEVELS,
        "en",
        19900,
        "0",
        CourseStatus.IN_REVIEW,
        ravi);
    em.flush();
    em.clear();
  }

  private List<String> slugs(Specification<Course> spec) {
    return courses.findAll(spec).stream().map(Course::slug).sorted().toList();
  }

  private Set<UUID> devopsTree() {
    return Set.copyOf(
        categories.findBySlugOrParentSlug("devops-cloud", "devops-cloud").stream()
            .map(Category::id)
            .toList());
  }

  @Test
  void eachLeafSelectsExactlyItsRows() {
    assertThat(slugs(published()))
        .containsExactly("cicd-hindi", "k8s-advanced", "k8s-basics", "react-100");
    assertThat(slugs(byInstructor(asha)))
        .containsExactly("aws-draft", "k8s-advanced", "k8s-basics");
    assertThat(slugs(inCategories(devopsTree())))
        .containsExactly("aws-draft", "cicd-hindi", "k8s-advanced", "k8s-basics");
    assertThat(slugs(atLevels(Set.of(CourseLevel.BEGINNER))))
        .containsExactly("cicd-hindi", "k8s-basics", "react-100");
    assertThat(slugs(inLanguage("hi"))).containsExactly("cicd-hindi");
    assertThat(slugs(free())).containsExactly("k8s-basics", "react-100");
    assertThat(slugs(paid()))
        .containsExactly("aws-draft", "cicd-hindi", "figma-review", "k8s-advanced");
    assertThat(slugs(ratedAtLeast(new BigDecimal("4.5"))))
        .containsExactly("k8s-advanced", "k8s-basics");
  }

  @Test
  void titleSearchIsCaseInsensitiveAndTreatsWildcardsLiterally() {
    assertThat(slugs(titleContains("KUBERNETES"))).containsExactly("k8s-advanced", "k8s-basics");
    // ⭐ "100%" must match the literal text, not "100 followed by anything" (Figma for 100 days)
    assertThat(slugs(titleContains("100%"))).containsExactly("react-100");
    assertThat(slugs(titleContains("_"))).isEmpty();
  }

  @Test
  void leavesComposeWithAndOrNot() {
    assertThat(
            slugs(Specification.allOf(published(), free(), atLevels(Set.of(CourseLevel.BEGINNER)))))
        .containsExactly("k8s-basics", "react-100");
    assertThat(slugs(inLanguage("hi").or(free())))
        .containsExactly("cicd-hindi", "k8s-basics", "react-100");
    assertThat(slugs(Specification.not(published()))).containsExactly("aws-draft", "figma-review");
    assertThat(slugs(Specification.allOf())).hasSize(6); // ⭐ AND of nothing = no restriction
  }

  /**
   * ⭐ The guard against the pattern's one real risk: a rule with two representations drifting
   * apart. For every kind of viewer, the SQL form returns exactly the rows the in-memory form
   * accepts.
   */
  @Test
  void theSqlAndInMemoryVisibilityRulesAgree() {
    List<Course> all = courses.findAll();
    List<Viewer> viewers =
        List.of(
            Viewer.anonymous(),
            new Viewer.Member(asha),
            new Viewer.Member(ravi),
            new Viewer.Member(UUID.randomUUID()),
            new Viewer.Admin(UUID.randomUUID()));

    for (Viewer viewer : viewers) {
      List<String> inMemory =
          all.stream().filter(viewer::canSee).map(Course::slug).sorted().toList();
      assertThat(slugs(visibleTo(viewer))).as("%s", viewer).isEqualTo(inMemory);
    }
    assertThat(slugs(visibleTo(new Viewer.Member(asha))))
        .contains("aws-draft") // her draft
        .doesNotContain("figma-review"); // not Ravi's course in review
  }

  @Test
  void aSearchComposesOnlyTheFacetsItHas() {
    assertThat(slugs(CourseSearch.everything().toSpecification(Set.of())))
        .containsExactly("cicd-hindi", "k8s-advanced", "k8s-basics", "react-100");

    CourseSearch everyFacet =
        new CourseSearch(
            " kubernetes ",
            "devops-cloud",
            Set.of(CourseLevel.BEGINNER),
            "en",
            PriceFilter.FREE,
            new BigDecimal("4"));
    assertThat(slugs(everyFacet.toSpecification(devopsTree()))).containsExactly("k8s-basics");

    CourseSearch paidInDevops =
        new CourseSearch(null, "devops-cloud", null, " ", PriceFilter.PAID, null);
    assertThat(slugs(paidInDevops.toSpecification(devopsTree())))
        .containsExactly("cicd-hindi", "k8s-advanced"); // the draft stays hidden
  }
}
