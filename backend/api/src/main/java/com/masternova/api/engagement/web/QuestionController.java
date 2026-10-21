package com.masternova.api.engagement.web;

import com.masternova.api.engagement.application.QuestionQueries;
import com.masternova.api.engagement.application.QuestionService;
import com.masternova.api.engagement.web.dto.AnswerRequest;
import com.masternova.api.engagement.web.dto.AnswerResponse;
import com.masternova.api.engagement.web.dto.AskQuestionRequest;
import com.masternova.api.engagement.web.dto.QuestionSummary;
import com.masternova.api.engagement.web.dto.QuestionThreadResponse;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.CursorPage;
import com.masternova.api.platform.IdempotencyKeyRequired;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A lecture's Q&A, for its enrolled learners, its instructor and admins. */
@RestController
@RequestMapping("/api/v1")
class QuestionController {

  private final QuestionService questions;
  private final QuestionQueries queries;

  QuestionController(QuestionService questions, QuestionQueries queries) {
    this.questions = questions;
    this.queries = queries;
  }

  /** Newest first, keyset-paged (API conventions §2). */
  @GetMapping("/lectures/{lectureId}/questions")
  CursorPage<QuestionSummary> list(
      CurrentUser me,
      @PathVariable UUID lectureId,
      @RequestParam(required = false) @Size(max = 300) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
    var result = queries.page(me, lectureId, cursor, limit);
    return CursorPage.of(
        result.page().items(),
        result.page().nextCursor(),
        q -> QuestionSummary.from(q, result.instructorId()));
  }

  /** Unsafe and unversioned: a double-clicked "Post" with the same key posts once (§4). */
  @PostMapping("/lectures/{lectureId}/questions")
  @IdempotencyKeyRequired
  ResponseEntity<QuestionSummary> ask(
      CurrentUser me, @PathVariable UUID lectureId, @Valid @RequestBody AskQuestionRequest body) {
    var posted = questions.ask(me, lectureId, body.title(), body.body());
    return ResponseEntity.created(URI.create("/api/v1/questions/" + posted.post().id()))
        .body(QuestionSummary.from(posted.post(), posted.instructorId()));
  }

  @GetMapping("/questions/{questionId}")
  QuestionThreadResponse thread(CurrentUser me, @PathVariable UUID questionId) {
    return QuestionThreadResponse.from(queries.thread(me, questionId));
  }

  /** 422 {@code REPLY_TOO_DEEP} below depth 3; 404 for a parent from another thread. */
  @PostMapping("/questions/{questionId}/answers")
  @IdempotencyKeyRequired
  ResponseEntity<AnswerResponse> answer(
      CurrentUser me, @PathVariable UUID questionId, @Valid @RequestBody AnswerRequest body) {
    var posted = questions.answer(me, questionId, body.parentAnswerId(), body.body());
    return ResponseEntity.created(URI.create("/api/v1/questions/" + questionId))
        .body(AnswerResponse.from(posted.post(), posted.instructorId()));
  }
}
