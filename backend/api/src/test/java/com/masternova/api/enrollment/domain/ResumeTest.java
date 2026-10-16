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
    assertThat(Resume.lecture(COURSE, Optional.empty(), false)).contains(A);
  }

  @Test
  void anUnfinishedLectureIsContinuedAFinishedOneMovesOn() {
    assertThat(Resume.lecture(COURSE, Optional.of(B), false)).contains(B);
    assertThat(Resume.lecture(COURSE, Optional.of(B), true)).contains(C);
  }

  @Test
  void theLastLectureFinishedLeavesNothingToContinue() {
    assertThat(Resume.lecture(COURSE, Optional.of(C), true)).isEmpty();
    assertThat(Resume.lecture(List.of(), Optional.empty(), false)).isEmpty();
  }

  @Test
  void aLectureNoLongerInTheCurriculumStartsOver() {
    assertThat(Resume.lecture(COURSE, Optional.of(UUID.randomUUID()), true)).contains(A);
  }

  @Test
  void theLastTenSecondsRestartTheLecture() {
    assertThat(Resume.position(125, 600)).isEqualTo(125);
    assertThat(Resume.position(589, 600)).isEqualTo(589);
    assertThat(Resume.position(590, 600)).isZero();
    assertThat(Resume.position(125, 0)).isEqualTo(125); // no known length: trust the position
  }
}
