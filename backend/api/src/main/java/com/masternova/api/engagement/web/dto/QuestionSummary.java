package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.Question;
import java.time.Instant;
import java.util.UUID;

/** A question in a lecture's list (and the head of its thread). */
public record QuestionSummary(
    UUID id,
    UUID lectureId,
    String title,
    String body,
    String authorName,
    boolean byInstructor,
    int answerCount,
    Instant createdAt,
    Instant lastActivityAt) {

  /** ⭐ "by the instructor" is decided at read time: the course's teacher now, not then. */
  public static QuestionSummary from(Question q, UUID instructorId) {
    return new QuestionSummary(
        q.id(),
        q.lectureId(),
        q.title(),
        q.body(),
        q.authorName(),
        q.authorId().equals(instructorId),
        q.answerCount(),
        q.createdAt(),
        q.lastActivityAt());
  }
}
