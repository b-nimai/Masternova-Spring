package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.kernel.money.Money;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The catalog mapping against the REAL schema (V6) — Hibernate's {@code validate} already proved
 * the columns exist; these prove the values survive the round trip and the database enforces what
 * the aggregate promises.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogPersistenceIT {

  static final Instant NOW = Instant.parse("2026-10-02T10:15:30.123456789Z");

  @Autowired TestEntityManager em;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;

  @Autowired DataSource dataSource;

  Instructor instructor;

  @BeforeEach
  void instructor() {
    instructor = new TestInstructors(dataSource).create("Asha Rao");
  }

  private CourseBuilder k8s(String slug) {
    return aCourse()
        .slug(slug)
        .priced(149900)
        .in(categories.findBySlug("containers-kubernetes").orElseThrow())
        .by(instructor)
        .createdAt(NOW);
  }

  @Test
  void theSeededCategoryTreeHasTwoLevels() {
    assertThat(categories.findBySlugOrParentSlug("devops-cloud", "devops-cloud"))
        .extracting(Category::slug)
        .containsExactlyInAnyOrder(
            "devops-cloud", "containers-kubernetes", "ci-cd", "cloud-platforms");
    assertThat(categories.findBySlug("ci-cd").orElseThrow().parent())
        .map(Category::slug)
        .contains("devops-cloud");
  }

  @Test
  void anAggregateRoundTripsWithItsValueObjectsAndOrder() {
    Course course =
        k8s("k8s-zero")
            .withSection(
                "Intro", aLecture("Welcome").preview().seconds(90).asset(UUID.randomUUID()))
            .withSection("Core", aLecture("Pods").seconds(600), aLecture("Cheat sheet").article())
            .build();
    courses.saveAndFlush(course); // ⭐ one save: cascade = ALL persists 2 sections + 3 lectures
    em.clear();

    Course loaded = courses.findWithCurriculumBySlug("k8s-zero").orElseThrow();

    assertThat(loaded.price()).isEqualTo(Money.of(149900, "INR")); // ⭐ @Embeddable record
    assertThat(loaded.totalDuration()).isEqualTo(LectureDuration.ofSeconds(690)); // converter
    assertThat(loaded.lectureCount()).isEqualTo(3);
    assertThat(loaded.createdAt()).isEqualTo(Instant.parse("2026-10-02T10:15:30.123456Z"));
    assertThat(loaded.version()).isZero();
    assertThat(loaded.sections()).extracting(Section::title).containsExactly("Intro", "Core");
    assertThat(loaded.sections().get(1).lectures())
        .extracting(Lecture::title)
        .containsExactly("Pods", "Cheat sheet"); // @OrderBy("position")
    assertThat(loaded.sections().get(0).lectures().getFirst().isPreview()).isTrue();
  }

  @Test
  void theDatabaseRefusesASecondCourseWithTheSameSlug() {
    courses.saveAndFlush(k8s("k8s-zero").build());

    // ⭐ the unique constraint is the real guard; Spring translates Hibernate's exception into
    //    its DataAccessException hierarchy
    assertThatThrownBy(() -> courses.saveAndFlush(k8s("k8s-zero").build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aPublishedCourseAlwaysHasItsSortKey() {
    courses.saveAndFlush(k8s("k8s-zero").published(NOW).build());

    // the CHECK constraint backs the aggregate: no row is PUBLISHED without published_at
    assertThatThrownBy(
            () ->
                em.getEntityManager()
                    .createNativeQuery("UPDATE course SET published_at = NULL")
                    .executeUpdate())
        .hasMessageContaining("course_published_at_ck");
  }
}
