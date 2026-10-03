package com.masternova.api.catalog.infrastructure;

import com.masternova.api.catalog.domain.CourseEditLog;
import com.masternova.api.catalog.domain.CurriculumCommand;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CourseEditLog} over JDBC: the history is append-mostly JSON queried by course and order —
 * not an entity graph, so no JPA mapping. Commands go in and out as JSON through the same Jackson
 * polymorphism the API uses ({@code "kind"}), so a stored edit is exactly what a client could send.
 * Runs in the caller's transaction (the edit and its history commit together).
 */
@Repository
class JdbcCourseEditLog implements CourseEditLog {

  private final JdbcClient jdbc;
  private final JsonMapper json;

  JdbcCourseEditLog(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void record(
      UUID courseId,
      CurriculumCommand command,
      CurriculumCommand inverse,
      long versionAfter,
      UUID actorId,
      Instant now) {
    jdbc.sql(
            """
            INSERT INTO course_edit (id, course_id, seq, command, inverse, version_after, actor_id, created_at)
            VALUES (:id, :course,
                    (SELECT coalesce(max(seq), 0) + 1 FROM course_edit WHERE course_id = :course),
                    CAST(:command AS jsonb), CAST(:inverse AS jsonb), :version, :actor, :now)
            """)
        .param("id", UUID.randomUUID())
        .param("course", courseId)
        .param("command", write(command))
        .param("inverse", write(inverse))
        .param("version", versionAfter)
        .param("actor", actorId)
        .param("now", java.sql.Timestamp.from(now))
        .update(); // ⭐ seq via max()+1 is safe: the course row is locked for the edit (lockById)
  }

  @Override
  public Optional<Entry> lastDone(UUID courseId) {
    return one(
        "SELECT id, seq, command::text, inverse::text FROM course_edit"
            + " WHERE course_id = :course AND undone_at IS NULL ORDER BY seq DESC LIMIT 1",
        courseId);
  }

  @Override
  public Optional<Entry> firstUndone(UUID courseId) {
    return one(
        "SELECT id, seq, command::text, inverse::text FROM course_edit"
            + " WHERE course_id = :course AND undone_at IS NOT NULL ORDER BY seq ASC LIMIT 1",
        courseId);
  }

  @Override
  public void markUndone(UUID editId, Instant now) {
    jdbc.sql("UPDATE course_edit SET undone_at = :now WHERE id = :id")
        .param("now", java.sql.Timestamp.from(now))
        .param("id", editId)
        .update();
  }

  @Override
  public void markRedone(UUID editId, CurriculumCommand freshInverse) {
    jdbc.sql(
            "UPDATE course_edit SET undone_at = NULL, inverse = CAST(:inverse AS jsonb) WHERE id = :id")
        .param("inverse", write(freshInverse))
        .param("id", editId)
        .update();
  }

  @Override
  public void discardRedo(UUID courseId) {
    jdbc.sql("DELETE FROM course_edit WHERE course_id = :course AND undone_at IS NOT NULL")
        .param("course", courseId)
        .update();
  }

  @Override
  public boolean canUndo(UUID courseId) {
    return exists(courseId, "undone_at IS NULL");
  }

  @Override
  public boolean canRedo(UUID courseId) {
    return exists(courseId, "undone_at IS NOT NULL");
  }

  private boolean exists(UUID courseId, String condition) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM course_edit WHERE course_id = :course AND "
                + condition
                + ")")
        .param("course", courseId)
        .query(Boolean.class)
        .single();
  }

  private Optional<Entry> one(String sql, UUID courseId) {
    return jdbc.sql(sql)
        .param("course", courseId)
        .query(
            (rs, n) ->
                new Entry(
                    rs.getObject(1, UUID.class),
                    rs.getLong(2),
                    json.readValue(rs.getString(3), CurriculumCommand.class),
                    json.readValue(rs.getString(4), CurriculumCommand.class)))
        .optional();
  }

  private String write(CurriculumCommand command) {
    // ⭐ serialise AS the sealed interface, so the "kind" discriminator is written
    return json.writerFor(CurriculumCommand.class).writeValueAsString(command);
  }
}
