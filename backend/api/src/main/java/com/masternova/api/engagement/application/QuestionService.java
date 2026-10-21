package com.masternova.api.engagement.application;

import com.masternova.api.engagement.domain.Answer;
import com.masternova.api.engagement.domain.CourseRef;
import com.masternova.api.engagement.domain.Question;
import com.masternova.api.engagement.domain.QuestionRepository;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.IdentityApi;
import com.masternova.api.platform.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts to a lecture's Q&A: asking, answering, replying. Each post is one transaction (the row and
 * the question's counters); a retry is made safe by the controller's Idempotency-Key.
 */
@Service
public class QuestionService {

  private final Participants participants;
  private final QuestionRepository questions;
  private final IdentityApi identity;
  private final Clock clock;

  QuestionService(
      Participants participants, QuestionRepository questions, IdentityApi identity, Clock clock) {
    this.participants = participants;
    this.questions = questions;
    this.identity = identity;
    this.clock = clock;
  }

  /** What was posted, and who teaches the course (the response flags the instructor's posts). */
  public record Posted<T>(T post, UUID instructorId) {}

  /** 404 for a lecture not in a published course; 403 {@code NOT_ENROLLED}. */
  @Transactional
  public Posted<Question> ask(CurrentUser me, UUID lectureId, String title, String body) {
    CourseRef course = participants.ofLecture(me, lectureId);
    Question question =
        Question.ask(course.courseId(), lectureId, me.id(), name(me), title, body, now());
    questions.insert(question);
    return new Posted<>(question, course.instructorId());
  }

  /**
   * Answers the question ({@code parentId == null}) or replies to one of its answers.
   *
   * @throws com.masternova.api.platform.RuleViolationException 422 {@code REPLY_TOO_DEEP}
   */
  @Transactional
  public Posted<Answer> answer(CurrentUser me, UUID questionId, UUID parentId, String body) {
    Question question =
        questions.find(questionId).orElseThrow(() -> new NotFoundException("Question", questionId));
    CourseRef course = participants.ofLecture(me, question.lectureId()); // as for asking
    Instant now = now();
    Answer answer =
        parentId == null
            ? Answer.to(question, me.id(), name(me), body, now)
            : questions
                .findAnswer(questionId, parentId) // ⭐ only an answer of THIS thread
                .orElseThrow(() -> new NotFoundException("Answer", parentId))
                .reply(me.id(), name(me), body, now);
    questions.insert(answer);
    return new Posted<>(answer, course.instructorId());
  }

  private String name(CurrentUser me) {
    return identity.displayName(me.id()).orElse("A learner");
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS); // what Postgres keeps
  }
}
