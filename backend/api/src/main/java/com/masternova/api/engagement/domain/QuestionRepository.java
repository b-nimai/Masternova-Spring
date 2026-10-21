package com.masternova.api.engagement.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Questions and their answer trees. */
public interface QuestionRepository {

  void insert(Question question);

  Optional<Question> find(UUID questionId);

  /** A lecture's questions, newest first, after the cursor ({@code null}: from the top). */
  List<Question> page(UUID lectureId, Instant beforeAt, UUID beforeId, int limit);

  /** An answer of THIS question (an id from another thread is empty). */
  Optional<Answer> findAnswer(UUID questionId, UUID answerId);

  /** Inserts the answer and moves the question's {@code answer_count} and last activity. */
  void insert(Answer answer);

  /**
   * The question's answers in READING ORDER (depth-first: each answer, then its replies, oldest
   * first), at most {@code limit} — one recursive query.
   */
  List<Answer> thread(UUID questionId, int limit);
}
