package com.masternova.java.sealed;

/** Operations over {@link LectureContent}, by type pattern. */
public final class LectureContents {

  private static final int WORDS_PER_MINUTE = 200;

  private LectureContents() {}

  /** Rough time a learner needs, in minutes — shown on the course page. */
  public static int estimatedMinutes(LectureContent content) {
    // ⭐ Exhaustive over a sealed CLASS hierarchy: Video + Article + Quiz cover everything, because
    //    QuizContent (non-sealed) also covers ALL of its subclasses. No `default` needed.
    return switch (content) {
      case VideoContent video -> ceilDiv(video.length().seconds(), 60);
      case ArticleContent article -> Math.max(1, ceilDiv(article.wordCount(), WORDS_PER_MINUTE));
      // ⭐ Order matters: CodingExercise IS-A QuizContent, so the more specific case must come
      //    first. Swap these two lines and the compiler reports "this case label is dominated".
      case CodingExercise exercise -> exercise.questions() * 10;
      case QuizContent quiz -> quiz.questions() * 2;
    };
  }

  private static int ceilDiv(int value, int divisor) {
    return Math.ceilDiv(value, divisor); // Java 18+: integer division rounding UP (7 / 2 → 4)
  }
}
