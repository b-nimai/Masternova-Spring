package com.masternova.java.sealed;

/** A quiz where each question is a small coding task. Possible because QuizContent is non-sealed. */
public class CodingExercise extends QuizContent {

  public CodingExercise(String title, int problems) {
    super(title, problems);
  }
}
