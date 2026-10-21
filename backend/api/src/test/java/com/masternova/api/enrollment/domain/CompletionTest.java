package com.masternova.api.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompletionTest {

  @Test
  void ninetyPercentOfAKnownDurationCompletes() {
    assertThat(Completion.isComplete(540, 600)).isTrue(); // exactly 90 %
    assertThat(Completion.isComplete(539, 600)).isFalse();
    assertThat(Completion.isComplete(9, 10)).isTrue();
    assertThat(Completion.isComplete(86_400, 86_400)).isTrue(); // no overflow at the bounds
  }

  @Test
  void aLectureWithNoKnownLengthCannotBeCompletedByWatching() {
    assertThat(Completion.isComplete(1_000, 0)).isFalse();
    assertThat(Completion.isTrackable(true, 0)).isFalse();
    assertThat(Completion.isTrackable(false, 600)).isFalse(); // an article's estimate
    assertThat(Completion.isTrackable(true, 600)).isTrue();
  }

  @Test
  void percentRoundsDownAndNeverPassesAHundred() {
    assertThat(Completion.percent(1, 3)).isEqualTo(33);
    assertThat(Completion.percent(3, 3)).isEqualTo(100);
    assertThat(Completion.percent(5, 3)).isEqualTo(100); // the curriculum shrank since
    assertThat(Completion.percent(0, 0)).isZero();
  }
}
