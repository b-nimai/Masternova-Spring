package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.kernel.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The aggregate's rules, with no Spring and no database (note 12 §1). */
class CourseTest {

  static final Instant NOW = Instant.parse("2026-10-02T10:15:30.123456789Z");
  static final Instructor ASHA = new Instructor(UUID.randomUUID(), "Asha Rao");

  /** Through the builder: only the two values these tests vary are named. */
  private static Course draft(String slug, String language) {
    return aCourse()
        .slug(slug)
        .language(language)
        .by(ASHA)
        .description("  Pods and services.  ")
        .createdAt(NOW)
        .build();
  }

  @Test
  void aNewCourseIsAnEmptyDraft() {
    Course course = draft("k8s-zero", "en");

    assertThat(course.status()).isEqualTo(CourseStatus.DRAFT);
    assertThat(course.isPublished()).isFalse();
    assertThat(course.publishedAt()).isEmpty();
    assertThat(course.lectureCount()).isZero();
    assertThat(course.totalDuration()).isEqualTo(LectureDuration.ZERO);
    assertThat(course.ratingAverage()).isEqualByComparingTo("0");
    assertThat(course.description()).isEqualTo("Pods and services.");
    assertThat(course.instructor()).isEqualTo(ASHA);
    assertThat(course.isOwnedBy(ASHA.id())).isTrue();
    assertThat(course.isOwnedBy(UUID.randomUUID())).isFalse();
    assertThat(course.createdAt()).isEqualTo(Instant.parse("2026-10-02T10:15:30.123456Z"));
    assertThat(course.updatedAt()).isEqualTo(course.createdAt());
  }

  @Test
  void slugsAndLanguagesHaveAShape() {
    assertThatThrownBy(() -> draft("Kubernetes Zero", "en"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slug");
    assertThatThrownBy(() -> draft("k8s--zero", "en")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> draft(null, "en")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> draft("a".repeat(141), "en"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> draft("k8s-zero", "english"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("language");
    assertThatThrownBy(() -> draft("k8s-zero", null)).isInstanceOf(IllegalArgumentException.class);
  }

  /** Price sorts compare minor units in SQL: they only mean something within one currency. */
  @Test
  void everyCatalogPriceIsInTheCatalogCurrency() {
    assertThatThrownBy(
            () ->
                Course.draft(
                    "usd-course",
                    "Priced in dollars",
                    "…",
                    CourseLevel.BEGINNER,
                    "en",
                    Money.of(1999, "USD"),
                    new Category(),
                    ASHA,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INR");
  }

  @Test
  void addingLecturesThroughTheRootKeepsTheRollupsTrue() {
    Course course = draft("k8s-zero", "en");
    Section intro = course.addSection("Intro");
    Section core = course.addSection(" Core ");

    course.addLecture(
        intro, "Welcome", LectureKind.VIDEO, true, LectureDuration.ofSeconds(90), null);
    UUID asset = UUID.randomUUID();
    Lecture pods =
        course.addLecture(
            core, "Pods", LectureKind.VIDEO, false, LectureDuration.ofSeconds(600), asset);

    assertThat(course.lectureCount()).isEqualTo(2);
    assertThat(course.totalDuration()).isEqualTo(LectureDuration.ofSeconds(690));
    assertThat(core.title()).isEqualTo("Core");
    assertThat(core.position()).isEqualTo(1);
    assertThat(pods.position()).isZero();
    assertThat(pods.section()).isSameAs(core);
    assertThat(pods.assetId()).contains(asset);
    assertThat(pods.kind()).isEqualTo(LectureKind.VIDEO);
    assertThat(intro.lectures().getFirst().assetId()).isEmpty();
  }

  @Test
  void theCurriculumCantBeChangedBehindTheRootsBack() {
    Course course = draft("k8s-zero", "en");
    Section mine = course.addSection("Intro");
    Section theirs = draft("other", "en").addSection("Intro");

    assertThatThrownBy(
            () ->
                course.addLecture(
                    theirs, "Sneaky", LectureKind.VIDEO, false, LectureDuration.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> course.sections().add(mine))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> mine.lectures().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> course.addSection(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void publishStampsTheSortKeyOnceAndARepublishKeepsIt() {
    Course course =
        aCourse().createdAt(NOW).published(NOW).build(); // DRAFT → IN_REVIEW → PUBLISHED

    course.transition(CourseAction.UNPUBLISH, NOW.plusSeconds(60));
    course.transition(CourseAction.SUBMIT, NOW.plusSeconds(120));
    course.transition(CourseAction.PUBLISH, NOW.plusSeconds(3600));

    assertThat(course.isPublished()).isTrue();
    assertThat(course.publishedAt()).contains(Instant.parse("2026-10-02T10:15:30.123456Z"));
    assertThat(course.updatedAt()).isAfter(course.createdAt());
  }

  @Test
  void theRatingSummaryIsConsistent() {
    Course course = draft("k8s-zero", "en");
    course.updateRatingSummary(new BigDecimal("4.567"), 12);
    assertThat(course.ratingAverage()).isEqualByComparingTo("4.57");
    assertThat(course.ratingCount()).isEqualTo(12);

    assertThatThrownBy(() -> course.updateRatingSummary(new BigDecimal("5.1"), 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> course.updateRatingSummary(new BigDecimal("-1"), 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> course.updateRatingSummary(BigDecimal.ONE, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> course.updateRatingSummary(BigDecimal.ZERO, 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> course.updateRatingSummary(BigDecimal.ONE, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void subtitlesAreOptionalAndBounded() {
    Course course = draft("k8s-zero", "en");
    assertThat(course.subtitle()).isEmpty();

    course.changeSubtitle("  Hands-on  ");
    assertThat(course.subtitle()).contains("Hands-on");
    course.changeSubtitle("   ");
    assertThat(course.subtitle()).isEmpty();
    course.changeSubtitle(null);
    assertThat(course.subtitle()).isEmpty();
    assertThatThrownBy(() -> course.changeSubtitle("x".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anInstructorNeedsAName() {
    assertThat(new Instructor(ASHA.id(), "  Asha  ").name()).isEqualTo("Asha");
    assertThatThrownBy(() -> new Instructor(ASHA.id(), " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Instructor(ASHA.id(), "x".repeat(101)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRootCategoryHasNoParent() {
    Category root = new Category();
    assertThat(root.isRoot()).isTrue();
    assertThat(root.parent()).isEmpty();
  }
}
