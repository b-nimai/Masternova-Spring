package com.masternova.java.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.valueobject.LectureDuration;
import org.junit.jupiter.api.Test;

class LectureContentsTest {

  @Test
  void videoRoundsUpToWholeMinutes() {
    VideoContent video = new VideoContent("Intro", LectureDuration.parse("4:05"));

    assertThat(LectureContents.estimatedMinutes(video)).isEqualTo(5);
  }

  @Test
  void articleAtTwoHundredWordsPerMinuteAtLeastOne() {
    assertThat(LectureContents.estimatedMinutes(new ArticleContent("Notes", 1_000))).isEqualTo(5);
    assertThat(LectureContents.estimatedMinutes(new ArticleContent("Tip", 30))).isEqualTo(1);
  }

  @Test
  void quizAndTheMoreSpecificCodingExercise() {
    assertThat(LectureContents.estimatedMinutes(new QuizContent("Check", 5))).isEqualTo(10);
    assertThat(LectureContents.estimatedMinutes(new CodingExercise("Kata", 3))).isEqualTo(30);
  }

  @Test
  void theSealIsRecordedInTheClassFile() {
    assertThat(LectureContent.class.isSealed()).isTrue();
    assertThat(LectureContent.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(VideoContent.class, ArticleContent.class, QuizContent.class);
    assertThat(QuizContent.class.isSealed()).isFalse(); // non-sealed: open again
  }
}
