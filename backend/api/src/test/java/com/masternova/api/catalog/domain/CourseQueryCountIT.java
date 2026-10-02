package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.loader.MultipleBagFetchException;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * ⭐ Fetch strategies, MEASURED: Hibernate's statistics count the SQL statements each way of loading
 * the course page sends. Study note: patterns/java/12-jpa-mapping-and-fetching.md §5.
 *
 * <p>If someone makes an association EAGER, removes the entity graph or the {@code @BatchSize},
 * these numbers change and the build fails — the N+1 can't come back silently.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CourseQueryCountIT {

  @Autowired TestEntityManager em;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired EntityManagerFactory emf;
  @Autowired DataSource dataSource;

  private Statistics stats;
  private Instructor instructor;

  @BeforeEach
  void setUp() {
    instructor = new TestInstructors(dataSource).create("Asha Rao");
    stats = emf.unwrap(SessionFactory.class).getStatistics();
  }

  /** A course with {@code sections × lecturesPerSection} lectures, flushed and detached. */
  private String seedCourse(String slug, String category, int sections, int lecturesPerSection) {
    courses.save(
        aCourse()
            .slug(slug)
            .in(categories.findBySlug(category).orElseThrow())
            .by(instructor)
            .withCurriculum(sections, lecturesPerSection)
            .build());
    em.flush();
    em.clear(); // ⭐ an empty persistence context: every load below really hits the database
    stats.clear();
    return slug;
  }

  private long statements() {
    return stats.getPrepareStatementCount();
  }

  /** Touches everything the course page renders. */
  private static int renderCoursePage(Course course) {
    int touched = course.category().name().length();
    for (Section section : course.sections()) {
      for (Lecture lecture : section.lectures()) {
        touched += lecture.title().length();
      }
    }
    return touched;
  }

  @Test
  void theCoursePageTakesTwoStatementsWhateverTheCurriculumSize() {
    String big = seedCourse("big", "ci-cd", 10, 5); // 50 lectures

    renderCoursePage(courses.findWithCurriculumBySlug(big).orElseThrow());

    // ⭐ 1: course JOIN category JOIN sections (entity graph)
    //    2: lectures WHERE section_id IN (10 ids)   (@BatchSize on Section.lectures)
    assertThat(statements()).isEqualTo(2);
  }

  @Test
  void aSmallCurriculumCostsTheSame() {
    String small = seedCourse("small", "ci-cd", 1, 1);

    renderCoursePage(courses.findWithCurriculumBySlug(small).orElseThrow());

    assertThat(statements()).isEqualTo(2); // constant, not proportional to the curriculum
  }

  @Test
  void withoutTheEntityGraphEveryLazyAssociationIsItsOwnStatement() {
    String big = seedCourse("big", "ci-cd", 10, 5);
    UUID id = courses.findWithCurriculumBySlug(big).orElseThrow().id();
    em.clear();
    stats.clear();

    renderCoursePage(courses.findById(id).orElseThrow());

    // course, then category, then sections, then ONE batched lecture query = 4.
    // (Without @BatchSize it would be 3 + 10: one lecture query PER SECTION — the N+1.)
    assertThat(statements()).isEqualTo(4);
  }

  @Test
  void aLazyToOneInAListIsOneStatementPerDistinctTarget() {
    seedCourse("a", "ci-cd", 0, 0);
    seedCourse("b", "ci-cd", 0, 0);
    seedCourse("c", "ui-ux", 0, 0);
    seedCourse("d", "marketing", 0, 0);

    List<Course> all = courses.findAll();
    all.forEach(c -> c.category().name());

    // ⭐ 1 for the courses + 1 per DISTINCT category (the persistence context loads each category
    //    once). 4 rows over 3 categories → 4 statements. The browse query fixes this with a fetch
    //    graph (5.5).
    assertThat(statements()).isEqualTo(1 + 3);
  }

  @Test
  void hibernateRefusesToJoinFetchTwoBagsAtOnce() {
    // ⭐ sections and lectures are both Lists without an index column ("bags"). Fetching both in
    //    one query would multiply rows (sections × lectures) with no way to de-duplicate a bag.
    assertThatThrownBy(
            () ->
                em.getEntityManager()
                    .createQuery(
                        "select c from Course c join fetch c.sections s join fetch s.lectures",
                        Course.class)
                    .getResultList())
        .hasRootCauseInstanceOf(MultipleBagFetchException.class);
  }
}
