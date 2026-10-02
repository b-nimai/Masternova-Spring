package com.masternova.api.learning.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * JPA/Hibernate behaviour you must understand before writing real modules — against a REAL
 * Postgres. Hibernate's statistics count the SQL statements, so every claim is measured.
 *
 * <p>@DataJpaTest = only the JPA slice (no web, no security), Flyway included, and each test runs
 * in a transaction that is ROLLED BACK at the end. The learning tables come from the TEST-ONLY
 * migration src/test/resources/db/migration/V1000__learning_jpa_tables.sql — even here Flyway owns
 * the schema and Hibernate only validates it. Study note §4–§8.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE) // the Testcontainers DB, not H2
@Import(TestcontainersConfiguration.class)
class JpaFundamentalsLearningIT {

  @Autowired TestEntityManager em;
  @Autowired LearningCourseRepository courses;
  @Autowired EntityManagerFactory emf;

  private Statistics stats;

  @BeforeEach
  void seedTenCoursesWithThreeSectionsEach() {
    for (int i = 1; i <= 10; i++) {
      LearningCourse course = new LearningCourse("Course " + i);
      course.addSection("Intro");
      course.addSection("Core");
      course.addSection("Wrap-up");
      em.persist(course); // cascade = ALL → the sections are persisted too
    }
    em.flush(); //  send the INSERTs now
    em.clear(); //  empty the persistence context, so every test starts from the database
    stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear(); // count only what the test itself does
  }

  private long queries() {
    return stats.getPrepareStatementCount();
  }

  @Test
  void thePersistenceContextReturnsTheSameObjectWithoutASecondQuery() {
    Long id = courses.findAll().getFirst().id();
    em.clear();
    stats.clear();

    LearningCourse first = em.find(LearningCourse.class, id);
    LearningCourse second = em.find(LearningCourse.class, id);

    // ⭐ FIRST-LEVEL CACHE: within one persistence context, one row = one Java object.
    assertThat(second).isSameAs(first);
    assertThat(queries()).isEqualTo(1);
  }

  @Test
  void dirtyCheckingSavesChangesWithoutCallingSave() {
    Long id = courses.findAll().getFirst().id();

    LearningCourse course = em.find(LearningCourse.class, id);
    course.rename("Renamed"); // no save(), no update() — the entity is MANAGED
    em.flush(); //               Hibernate compares with its snapshot and issues the UPDATE
    em.clear();

    LearningCourse reloaded = em.find(LearningCourse.class, id);
    assertThat(reloaded.title()).isEqualTo("Renamed");
    assertThat(reloaded.version()).isEqualTo(1); // @Version bumped by the UPDATE
  }

  @Test
  void changesToADetachedEntityAreIgnoredUntilMerged() {
    Long id = courses.findAll().getFirst().id();
    LearningCourse course = em.find(LearningCourse.class, id);
    em.detach(course); //       now DETACHED: Hibernate no longer tracks it

    course.rename("Lost change");
    em.flush();
    em.clear();
    assertThat(em.find(LearningCourse.class, id).title()).isEqualTo("Course 1");

    em.clear();
    LearningCourse managedCopy =
        em.getEntityManager().merge(course); // ⭐ merge returns a MANAGED copy
    em.flush();
    em.clear();
    assertThat(managedCopy).isNotSameAs(course);
    assertThat(em.find(LearningCourse.class, id).title()).isEqualTo("Lost change");
  }

  @Test
  void touchingALazyCollectionOutsideThePersistenceContextFails() {
    LearningCourse course = courses.findAll().getFirst(); // sections NOT loaded (lazy)
    em.clear(); //                                          the "session" is gone for this entity

    // ⭐ This is what happens in a controller when open-in-view is off (we set it off on purpose).
    assertThatThrownBy(() -> course.sections().size())
        .isInstanceOf(LazyInitializationException.class);
  }

  @Test
  void theNPlusOneProblem() {
    List<LearningCourse> all = courses.findAll(); //        1 query for the courses …
    int totalSections = all.stream().mapToInt(c -> c.sections().size()).sum(); // … + 1 PER course

    assertThat(totalSections).isEqualTo(30);
    assertThat(queries()).isEqualTo(1 + 10); // ⚠️ N+1: 11 round-trips for one screen
  }

  @Test
  void joinFetchLoadsEverythingInOneQuery() {
    List<LearningCourse> all = courses.findAllWithSections();
    int totalSections = all.stream().mapToInt(c -> c.sections().size()).sum();

    assertThat(all).hasSize(10);
    assertThat(totalSections).isEqualTo(30);
    assertThat(queries()).isEqualTo(1); // ✅
  }

  @Test
  void anEntityGraphDoesTheSame() {
    List<LearningCourse> all = courses.findAllWithSectionsGraph();

    assertThat(all.stream().mapToInt(c -> c.sections().size()).sum()).isEqualTo(30);
    assertThat(queries()).isEqualTo(1); // ✅
  }

  @Test
  void derivedQueriesComeFromTheMethodName() {
    assertThat(courses.findByTitleContainingIgnoreCaseOrderByTitle("course 1"))
        .extracting(LearningCourse::title)
        .containsExactly("Course 1", "Course 10");
  }
}
