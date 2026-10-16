package com.masternova.api.enrollment.infrastructure;

import com.masternova.api.enrollment.domain.LectureProgressRepository;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import com.masternova.api.enrollment.domain.TrackedProgress;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code lecture_progress} in plain SQL. ⭐ A whole flush batch is ONE upsert over {@code unnest}ed
 * arrays — one round trip and one statement whatever the batch size — and its {@code SET} clause is
 * {@link ProgressUpdate#merge} in SQL.
 */
@Repository
class JdbcLectureProgressRepository implements LectureProgressRepository {

  private final JdbcClient jdbc;

  JdbcLectureProgressRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The merge, row by row:
   *
   * <ul>
   *   <li>{@code max = GREATEST(old, new)} — monotonic;
   *   <li>{@code last} from whichever is newer by {@code (updated_at, last)} — a ROW comparison,
   *       the same tie-break as Java and Lua;
   *   <li>{@code completed_at} = the old one if any, else this batch's (if it completes it).
   * </ul>
   */
  @Override
  public void upsert(Collection<TrackedProgress> rows) {
    if (rows.isEmpty()) {
      return;
    }
    List<TrackedProgress> list = List.copyOf(rows);
    jdbc.sql(
            """
            INSERT INTO lecture_progress (user_id, lecture_id, course_id, last_position_seconds,
                                          max_position_seconds, updated_at, completed_at)
            SELECT u, l, c, last, mx, at, CASE WHEN done THEN at END
            FROM unnest(:users, :lectures, :courses, :lasts, :maxes, :ats, :done)
                 AS b (u, l, c, last, mx, at, done)
            ON CONFLICT (user_id, lecture_id) DO UPDATE SET
              max_position_seconds =
                GREATEST(lecture_progress.max_position_seconds, EXCLUDED.max_position_seconds),
              last_position_seconds =
                CASE WHEN (EXCLUDED.updated_at, EXCLUDED.last_position_seconds)
                          > (lecture_progress.updated_at, lecture_progress.last_position_seconds)
                     THEN EXCLUDED.last_position_seconds
                     ELSE lecture_progress.last_position_seconds END,
              updated_at = GREATEST(lecture_progress.updated_at, EXCLUDED.updated_at),
              completed_at = COALESCE(lecture_progress.completed_at, EXCLUDED.completed_at)
            """)
        .param("users", array("uuid", list, r -> r.update().userId()))
        .param("lectures", array("uuid", list, r -> r.update().lectureId()))
        .param("courses", array("uuid", list, TrackedProgress::courseId))
        .param("lasts", array("int4", list, r -> r.update().lastPositionSeconds()))
        .param("maxes", array("int4", list, r -> r.update().maxPositionSeconds()))
        .param("ats", array("timestamptz", list, r -> Timestamp.from(r.update().at())))
        .param("done", array("bool", list, TrackedProgress::isComplete))
        .update();
  }

  @Override
  public Map<UUID, ProgressUpdate> find(UUID userId, Collection<UUID> lectureIds) {
    if (lectureIds.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(
            """
            SELECT lecture_id, last_position_seconds, max_position_seconds, updated_at
            FROM lecture_progress WHERE user_id = :user AND lecture_id IN (:lectures)
            """)
        .param("user", userId)
        .param("lectures", List.copyOf(lectureIds))
        .query(
            (rs, n) ->
                new ProgressUpdate(
                    userId,
                    rs.getObject("lecture_id", UUID.class),
                    rs.getInt("last_position_seconds"),
                    rs.getInt("max_position_seconds"),
                    rs.getTimestamp("updated_at").toInstant()))
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableMap(ProgressUpdate::lectureId, Function.identity()));
  }

  private static <T> SqlTypeValue array(String type, List<T> rows, Function<T, Object> value) {
    return SqlArrays.of(type, rows.stream().map(value).toArray());
  }
}
