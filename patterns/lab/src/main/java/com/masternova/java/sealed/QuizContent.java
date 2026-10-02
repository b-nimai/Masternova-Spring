package com.masternova.java.sealed;

/**
 * A quiz. {@code non-sealed}: the seal is deliberately re-opened here so new quiz kinds (see
 * {@link CodingExercise}) can be added without touching {@link LectureContent}.
 */
public non-sealed class QuizContent extends LectureContent {

  private final int questions;

  public QuizContent(String title, int questions) {
    super(title);
    if (questions < 1) {
      throw new IllegalArgumentException("a quiz needs at least one question, was " + questions);
    }
    this.questions = questions;
  }

  public int questions() {
    return questions;
  }
}
