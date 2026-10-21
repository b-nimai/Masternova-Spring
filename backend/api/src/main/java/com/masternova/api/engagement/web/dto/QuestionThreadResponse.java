package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.application.QuestionQueries.QuestionThread;
import java.util.List;

/**
 * A question and its answers in reading order; {@code truncated} when it has more than the 500
 * shown.
 */
public record QuestionThreadResponse(
    QuestionSummary question, List<AnswerResponse> answers, boolean truncated) {

  public static QuestionThreadResponse from(QuestionThread t) {
    return new QuestionThreadResponse(
        QuestionSummary.from(t.question(), t.instructorId()),
        t.answers().stream().map(a -> AnswerResponse.from(a, t.instructorId())).toList(),
        t.truncated());
  }
}
