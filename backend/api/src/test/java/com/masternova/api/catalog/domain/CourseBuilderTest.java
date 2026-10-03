package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.kernel.money.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The builder itself: valid defaults, unique slugs, and every course built the legal way. */
class CourseBuilderTest {

  @Test
  void theDefaultsMakeAValidFreeDraft() {
    Course course = aCourse().build();

    assertThat(course.status()).isEqualTo(CourseStatus.DRAFT);
    assertThat(course.price()).isEqualTo(Money.zero("INR"));
    assertThat(course.sections()).isEmpty();
  }

  @Test
  void slugsAreUniqueSoSavingTwoDefaultCoursesNeverCollides() {
    assertThat(aCourse().build().slug()).isNotEqualTo(aCourse().build().slug());
  }

  @Test
  void aBuiltCurriculumKeepsTheAggregatesRollups() {
    Course course =
        aCourse()
            .withSection(
                "Intro", aLecture("Welcome").preview().seconds(90), aLecture("Notes").article())
            .withCurriculum(2, 3)
            .published(Instant.parse("2026-10-02T10:00:00Z"))
            .build();

    // ⭐ built through addLecture, so the rollups are right without the builder computing them
    assertThat(course.lectureCount()).isEqualTo(2 + 6);
    assertThat(course.totalDuration().seconds()).isEqualTo(90 + 6 * 60);
    assertThat(course.isPublished()).isTrue();
  }
}
