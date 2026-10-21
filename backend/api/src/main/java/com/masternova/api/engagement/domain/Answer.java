package com.masternova.api.engagement.domain;

import com.masternova.api.platform.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An answer to a question ({@code depth} 1) or a reply to an answer (2–3). ⭐ The depth cap keeps a
 * thread readable on a phone and bounds the recursive read (LLD §3).
 *
 * @param parentId the answer replied to; {@code null} for an answer to the question
 */
public record Answer(
    UUID id,
    UUID questionId,
    UUID parentId,
    int depth,
    UUID authorId,
    String authorName,
    String body,
    Instant createdAt) {

  public static final int MAX_DEPTH = 3;
  public static final int MAX_BODY = Question.MAX_BODY;

  public Answer {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(questionId, "questionId");
    if (depth < 1 || depth > MAX_DEPTH || (parentId == null) != (depth == 1)) {
      throw new IllegalArgumentException("an answer is depth 1, a reply 2–" + MAX_DEPTH);
    }
    Objects.requireNonNull(authorId, "authorId");
    Objects.requireNonNull(authorName, "authorName");
    body = Objects.requireNonNull(body, "body").strip();
    if (body.isEmpty() || body.length() > MAX_BODY) {
      throw new IllegalArgumentException("an answer is 1–" + MAX_BODY + " characters");
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** An answer to the question itself. */
  public static Answer to(Question q, UUID authorId, String authorName, String body, Instant now) {
    return new Answer(UUID.randomUUID(), q.id(), null, 1, authorId, authorName, body, now);
  }

  /**
   * A reply to this answer, one level deeper.
   *
   * @throws RuleViolationException 422 {@code REPLY_TOO_DEEP} below the cap
   */
  public Answer reply(UUID authorId, String authorName, String text, Instant now) {
    if (depth >= MAX_DEPTH) {
      throw new RuleViolationException(
          "REPLY_TOO_DEEP", "Replies go " + MAX_DEPTH + " levels deep: reply higher up.");
    }
    return new Answer(
        UUID.randomUUID(), questionId, id, depth + 1, authorId, authorName, text, now);
  }
}
