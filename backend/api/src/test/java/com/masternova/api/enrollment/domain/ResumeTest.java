package com.masternova.api.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResumeTest {

  static final UUID A = UUID.randomUUID();
  static final UUID B = UUID.randomUUID();
  static final UUID C = UUID.randomUUID();
  static final List<UUID> COURSE = List.of(A, B, C);

  @Test
  void nothingWatchedStartsAtTheFirstLecture() {
    assertThat(Resume.lecture(COURSE, Optional.empty(), false, 0)).contains(A);
  }

  @Test
  void anUnfinishedLectureIsContinuedAFinishedOneMovesOn() {
    assertThat(Resume.lecture(COURSE, Optional.of(B), false, 0)).contains(B);
    assertThat(Resume.lecture(COURSE, Optional.of(B), true, 2)).contains(C);
  }

  @Test
  void aCompleteCourseLeavesNothingToContinue() {
    assertThat(Resume.lecture(COURSE, Optional.of(C), true, 3)).isEmpty();
    assertThat(Resume.lecture(List.of(), Optional.empty(), false, 0)).isEmpty();
  }

  @Test
  void theLastLectureFinishedOfAnIncompleteCourseWrapsToTheFirst() {
    // they skipped ahead: the end is done, the start isn't — Continue must not say "Review"
    assertThat(Resume.lecture(COURSE, Optional.of(C), true, 1)).contains(A);
  }

  @Test
  void aLectureNoLongerInTheCurriculumStartsOver() {
    assertThat(Resume.lecture(COURSE, Optional.of(UUID.randomUUID()), true, 0)).contains(A);
  }

  @Test
  void theLastTenSecondsRestartTheLecture() {
    assertThat(Resume.position(125, 600)).isEqualTo(125);
    assertThat(Resume.position(589, 600)).isEqualTo(589);
    assertThat(Resume.position(590, 600)).isZero();
    assertThat(Resume.position(125, 0)).isEqualTo(125); // no known length: trust the position
  }
}
