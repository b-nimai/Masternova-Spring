package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ⭐ The Prototype, proven with no Spring and no database: what is copied DEEPLY, what is RESET, and
 * what is SHARED on purpose. Pattern note 11.
 */
class CourseDuplicationTest {

  static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");
  static final Instant COPIED = Instant.parse("2026-10-02T10:00:00Z");
  static final UUID VIDEO = UUID.randomUUID();

  Course source;

  @BeforeEach
  void aPublishedRatedCourse() {
    source =
        aCourse()
            .slug("k8s")
            .title("Kubernetes")
            .subtitle("Hands-on")
            .level(CourseLevel.INTERMEDIATE)
            .priced(149900)
            .createdAt(CREATED)
            .withSection("Intro", aLecture("Welcome").preview().seconds(90).asset(VIDEO))
            .withSection("Core", aLecture("Pods").seconds(600), aLecture("Services").seconds(300))
            .published(CREATED)
            .rated("4.70", 31)
            .build();
  }

  @Test
  void theCopyIsANewDraftWithNoHistory() {
    Course copy = source.duplicateAsDraft("k8s-copy-abc123", COPIED);

    assertThat(copy.id()).isNotEqualTo(source.id());
    assertThat(copy.slug()).isEqualTo("k8s-copy-abc123");
    assertThat(copy.title()).isEqualTo("Kubernetes (copy)");
    assertThat(copy.status()).isEqualTo(CourseStatus.DRAFT); // reset
    assertThat(copy.publishedAt()).isEmpty();
    assertThat(copy.ratingCount()).isZero();
    assertThat(copy.ratingAverage()).isEqualByComparingTo("0");
    assertThat(copy.enrollmentCount()).isZero();
    assertThat(copy.createdAt()).isEqualTo(COPIED);
    assertThat(copy.version()).isNull(); // new to JPA: save() will INSERT it
  }

  @Test
  void theContentAndCurriculumAreCopied() {
    Course copy = source.duplicateAsDraft("k8s-copy-abc123", COPIED);

    assertThat(copy.subtitle()).contains("Hands-on");
    assertThat(copy.description()).isEqualTo(source.description());
    assertThat(copy.level()).isEqualTo(CourseLevel.INTERMEDIATE);
    assertThat(copy.price()).isEqualTo(source.price());
    assertThat(copy.priceSetAt()).isEqualTo(source.priceSetAt()); // the pricing decision is content
    assertThat(copy.instructor()).isEqualTo(source.instructor());
    assertThat(copy.category()).isSameAs(source.category()); // another aggregate: referenced
    assertThat(copy.sections()).extracting(Section::title).containsExactly("Intro", "Core");
    assertThat(copy.sections().get(1).lectures())
        .extracting(Lecture::title, Lecture::duration, Lecture::position)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Pods", LectureDuration.ofSeconds(600), 0),
            org.assertj.core.groups.Tuple.tuple("Services", LectureDuration.ofSeconds(300), 1));
    assertThat(copy.lectureCount()).isEqualTo(3); // rollups recomputed from the copy
    assertThat(copy.totalDuration()).isEqualTo(LectureDuration.ofSeconds(990));
  }

  @Test
  void sectionsAndLecturesAreNewObjectsOwnedByTheCopy() {
    Course copy = source.duplicateAsDraft("k8s-copy-abc123", COPIED);

    Section copiedIntro = copy.sections().getFirst();
    Section sourceIntro = source.sections().getFirst();
    assertThat(copiedIntro).isNotSameAs(sourceIntro);
    assertThat(copiedIntro.id()).isNotEqualTo(sourceIntro.id());
    assertThat(copiedIntro.course()).isSameAs(copy); // ⭐ points at its NEW parent
    Lecture copiedWelcome = copiedIntro.lectures().getFirst();
    assertThat(copiedWelcome.id()).isNotEqualTo(sourceIntro.lectures().getFirst().id());
    assertThat(copiedWelcome.section()).isSameAs(copiedIntro);
  }

  /** ⭐ The deep-copy proof: changing the copy leaves the source untouched. */
  @Test
  void changingTheCopyNeverChangesTheSource() {
    Course copy = source.duplicateAsDraft("k8s-copy-abc123", COPIED);

    copy.addSection("Bonus");
    copy.addLecture(
        copy.sections().getFirst(),
        "Extra",
        LectureKind.ARTICLE,
        false,
        LectureDuration.ZERO,
        null);

    assertThat(source.sections()).hasSize(2);
    assertThat(source.sections().getFirst().lectures()).hasSize(1);
    assertThat(source.lectureCount()).isEqualTo(3);
  }

  /** ⭐ The deliberate SHALLOW edge, asserted so nobody "fixes" it into copying gigabytes. */
  @Test
  void mediaAssetsAreSharedNotCopied() {
    Course copy = source.duplicateAsDraft("k8s-copy-abc123", COPIED);

    assertThat(copy.sections().getFirst().lectures().getFirst().assetId()).contains(VIDEO);
  }

  @Test
  void aLongTitleStaysWithinTheLimit() {
    Course longTitle = aCourse().title("x".repeat(120)).build();

    assertThat(longTitle.duplicateAsDraft("long-copy", COPIED).title())
        .hasSize(120)
        .endsWith(" (copy)");
  }
}
