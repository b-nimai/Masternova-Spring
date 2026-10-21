package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.Answer;
import java.time.Instant;
import java.util.UUID;

/**
 * One answer or reply. A thread is a FLAT list in reading order: indent by {@code depth} (1–3), and
 * {@code parentId} says which answer a reply belongs to.
 */
public record AnswerResponse(
    UUID id,
    UUID parentId,
    int depth,
    String body,
    String authorName,
    boolean byInstructor,
    Instant createdAt) {

  public static AnswerResponse from(Answer a, UUID instructorId) {
    return new AnswerResponse(
        a.id(),
        a.parentId(),
        a.depth(),
        a.body(),
        a.authorName(),
        a.authorId().equals(instructorId),
        a.createdAt());
  }
}
