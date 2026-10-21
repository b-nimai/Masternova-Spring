package com.masternova.api.engagement.infrastructure;

import com.masternova.api.engagement.domain.Answer;
import com.masternova.api.engagement.domain.Question;
import com.masternova.api.engagement.domain.QuestionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Questions and answer trees in plain SQL: the thread is a recursive CTE JPA can't express. */
@Repository
class JdbcQuestionRepository implements QuestionRepository {

  private static final String QUESTION_COLUMNS =
      """
      id, course_id, lecture_id, author_id, author_name, title, body, answer_count, created_at,
      last_activity_at
      """;
  private static final String ANSWER_COLUMNS =
      "id, question_id, parent_id, depth, author_id, author_name, body, created_at";

  /**
   * ⭐ The thread in READING ORDER with one query. Each row carries the PATH from its root answer; a
   * path element is the row's time (fixed-width UTC text, so text order = time order) plus its id
   * (two answers in the same microsecond still order). {@code ORDER BY path} is then depth-first:
   * an answer, all its replies oldest first, the next answer. The depth cap (3) bounds the
   * recursion; the composite FK keeps every step inside this question.
   */
  private static final String THREAD =
      """
      WITH RECURSIVE thread AS (
          SELECT a.*, ARRAY[to_char(a.created_at AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISSUS')
                            || a.id::text] AS path
          FROM answer a
          WHERE a.question_id = :question AND a.parent_id IS NULL
        UNION ALL
          SELECT c.*, t.path || (to_char(c.created_at AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISSUS')
                                 || c.id::text)
          FROM answer c JOIN thread t ON c.question_id = t.question_id AND c.parent_id = t.id
      )
      SELECT %s FROM thread ORDER BY path LIMIT :limit
      """
          .formatted(ANSWER_COLUMNS);

  private final JdbcClient jdbc;

  JdbcQuestionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Question q) {
    jdbc.sql(
            """
            INSERT INTO question (id, course_id, lecture_id, author_id, author_name, title, body,
                                  answer_count, created_at, last_activity_at)
            VALUES (:id, :course, :lecture, :author, :name, :title, :body, 0, :at, :at)
            """)
        .param("id", q.id())
        .param("course", q.courseId())
        .param("lecture", q.lectureId())
        .param("author", q.authorId())
        .param("name", q.authorName())
        .param("title", q.title())
        .param("body", q.body())
        .param("at", Timestamp.from(q.createdAt()))
        .update();
  }

  @Override
  public Optional<Question> find(UUID questionId) {
    return jdbc.sql("SELECT " + QUESTION_COLUMNS + " FROM question WHERE id = :id")
        .param("id", questionId)
        .query((rs, n) -> question(rs))
        .optional();
  }

  /** ⭐ A row comparison: the scan of {@code question_lecture_newest_idx} starts AT the cursor. */
  @Override
  public List<Question> page(UUID lectureId, Instant beforeAt, UUID beforeId, int limit) {
    String after = beforeAt == null ? "" : " AND (created_at, id) < (:at, :id)";
    var sql =
        jdbc.sql(
                "SELECT "
                    + QUESTION_COLUMNS
                    + " FROM question WHERE lecture_id = :lecture"
                    + after
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit")
            .param("lecture", lectureId)
            .param("limit", limit);
    if (beforeAt != null) {
      sql = sql.param("at", Timestamp.from(beforeAt)).param("id", beforeId);
    }
    return sql.query((rs, n) -> question(rs)).list();
  }

  @Override
  public Optional<Answer> findAnswer(UUID questionId, UUID answerId) {
    return jdbc.sql(
            "SELECT " + ANSWER_COLUMNS + " FROM answer WHERE question_id = :question AND id = :id")
        .param("question", questionId)
        .param("id", answerId)
        .query((rs, n) -> answer(rs))
        .optional();
  }

  /** The insert and the question's counters, in the caller's transaction. */
  @Override
  public void insert(Answer a) {
    jdbc.sql(
            """
            INSERT INTO answer (id, question_id, parent_id, depth, author_id, author_name, body,
                                created_at)
            VALUES (:id, :question, :parent, :depth, :author, :name, :body, :at)
            """)
        .param("id", a.id())
        .param("question", a.questionId())
        .param("parent", a.parentId(), java.sql.Types.OTHER)
        .param("depth", a.depth())
        .param("author", a.authorId())
        .param("name", a.authorName())
        .param("body", a.body())
        .param("at", Timestamp.from(a.createdAt()))
        .update();
    // ⭐ relative, in SQL: two answers committing at once both count (no read-modify-write)
    jdbc.sql(
            """
            UPDATE question SET answer_count = answer_count + 1,
                                last_activity_at = GREATEST(last_activity_at, :at)
            WHERE id = :question
            """)
        .param("at", Timestamp.from(a.createdAt()))
        .param("question", a.questionId())
        .update();
  }

  @Override
  public List<Answer> thread(UUID questionId, int limit) {
    return jdbc.sql(THREAD)
        .param("question", questionId)
        .param("limit", limit)
        .query((rs, n) -> answer(rs))
        .list();
  }

  private static Question question(ResultSet rs) throws SQLException {
    return new Question(
        rs.getObject("id", UUID.class),
        rs.getObject("course_id", UUID.class),
        rs.getObject("lecture_id", UUID.class),
        rs.getObject("author_id", UUID.class),
        rs.getString("author_name"),
        rs.getString("title"),
        rs.getString("body"),
        rs.getInt("answer_count"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("last_activity_at").toInstant());
  }

  private static Answer answer(ResultSet rs) throws SQLException {
    return new Answer(
        rs.getObject("id", UUID.class),
        rs.getObject("question_id", UUID.class),
        rs.getObject("parent_id", UUID.class),
        rs.getInt("depth"),
        rs.getObject("author_id", UUID.class),
        rs.getString("author_name"),
        rs.getString("body"),
        rs.getTimestamp("created_at").toInstant());
  }
}
