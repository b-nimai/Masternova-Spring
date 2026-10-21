package com.masternova.api.engagement.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question asked under a lecture: the root of a thread.
 *
 * @param courseId copied from the lecture when asked (a lecture never changes course)
 * @param answerCount every answer and reply in the thread
 */
public record Question(
    UUID id,
    UUID courseId,
    UUID lectureId,
    UUID authorId,
    String authorName,
    String title,
    String body,
    int answerCount,
    Instant createdAt,
    Instant lastActivityAt) {

  public static final int MAX_TITLE = 150;
  public static final int MAX_BODY = 5000;

  public Question {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(lectureId, "lectureId");
    Objects.requireNonNull(authorId, "authorId");
    Objects.requireNonNull(authorName, "authorName");
    title = Objects.requireNonNull(title, "title").strip();
    if (title.isEmpty() || title.length() > MAX_TITLE) {
      throw new IllegalArgumentException("a question's title is 1–" + MAX_TITLE + " characters");
    }
    body = body == null ? "" : body.strip();
    if (body.length() > MAX_BODY) {
      throw new IllegalArgumentException("a question is at most " + MAX_BODY + " characters");
    }
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(lastActivityAt, "lastActivityAt");
  }

  public static Question ask(
      UUID courseId,
      UUID lectureId,
      UUID authorId,
      String authorName,
      String title,
      String body,
      Instant now) {
    return new Question(
        UUID.randomUUID(), courseId, lectureId, authorId, authorName, title, body, 0, now, now);
  }
}
