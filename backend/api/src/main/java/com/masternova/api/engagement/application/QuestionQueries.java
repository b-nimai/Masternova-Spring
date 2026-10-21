package com.masternova.api.engagement.application;

import com.masternova.api.engagement.domain.Answer;
import com.masternova.api.engagement.domain.CourseRef;
import com.masternova.api.engagement.domain.Question;
import com.masternova.api.engagement.domain.QuestionRepository;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.CursorPage;
import com.masternova.api.platform.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The read side of Q&A, for the lecture's participants only (it's not a public forum). */
@Service
@Transactional(readOnly = true)
public class QuestionQueries {

  /** One huge thread can't take a request down: the first 500 in reading order, then a flag. */
  static final int MAX_THREAD = 500;

  private final Participants participants;
  private final QuestionRepository questions;

  QuestionQueries(Participants participants, QuestionRepository questions) {
    this.participants = participants;
    this.questions = questions;
  }

  /** A page of the lecture's questions, plus who teaches the course (to flag their posts). */
  public record QuestionPage(CursorPage<Question> page, UUID instructorId) {}

  /** A question and its answers in reading order. */
  public record QuestionThread(
      Question question, List<Answer> answers, boolean truncated, UUID instructorId) {}

  public QuestionPage page(CurrentUser me, UUID lectureId, String cursor, int limit) {
    CourseRef course = participants.ofLecture(me, lectureId);
    CursorPage<Question> page =
        NewestFirstCursor.page(
            cursor,
            limit,
            (at, id, n) -> questions.page(lectureId, at, id, n),
            Question::createdAt,
            Question::id);
    return new QuestionPage(page, course.instructorId());
  }

  public QuestionThread thread(CurrentUser me, UUID questionId) {
    Question question =
        questions.find(questionId).orElseThrow(() -> new NotFoundException("Question", questionId));
    CourseRef course = participants.ofLecture(me, question.lectureId());
    List<Answer> answers = questions.thread(questionId, MAX_THREAD + 1);
    boolean truncated = answers.size() > MAX_THREAD;
    return new QuestionThread(
        question,
        truncated ? List.copyOf(answers.subList(0, MAX_THREAD)) : answers,
        truncated,
        course.instructorId());
  }
}
