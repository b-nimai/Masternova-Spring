package com.masternova.api.enrollment.infrastructure;

import com.masternova.api.enrollment.domain.Enrollment;
import com.masternova.api.enrollment.domain.EnrollmentRepository;
import com.masternova.api.enrollment.domain.EnrollmentStatus;
import com.masternova.api.enrollment.domain.UserCourse;
import com.masternova.api.entitlement.EntitlementSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The enrollment rows in Postgres, in plain SQL: each write is one upsert whose {@code WHERE} is
 * the ordering rule, so concurrent and repeated events serialise on the primary key.
 */
@Repository
class JdbcEnrollmentRepository implements EnrollmentRepository {

  private static final String COLUMNS =
      """
      user_id, course_id, source, status, enrolled_at, status_at, completed_lectures,
      last_lecture_id, last_activity_at
      """;

  private final JdbcClient jdbc;

  JdbcEnrollmentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Enrollment> find(UUID userId, UUID courseId) {
    return jdbc.sql(
            "SELECT " + COLUMNS + " FROM enrollment WHERE user_id = :user AND course_id = :course")
        .param("user", userId)
        .param("course", courseId)
        .query((rs, n) -> map(rs))
        .optional();
  }

  /** ⭐ One range scan of {@code enrollment_dashboard_idx} (V23). */
  @Override
  public List<Enrollment> active(UUID userId) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + """
                FROM enrollment WHERE user_id = :user AND status = 'ACTIVE'
                ORDER BY last_activity_at DESC NULLS LAST, enrolled_at DESC
                """)
        .param("user", userId)
        .query((rs, n) -> map(rs))
        .list();
  }

  /**
   * ⭐ Inserts, or revives a REVOKED row whose status is OLDER than this grant. An ACTIVE row, or a
   * revoke newer than this grant, matches nothing: 0 rows, "no change". {@code enrolled_at} keeps
   * the first enrollment (a re-purchase is not a new learner).
   */
  @Override
  public boolean enroll(UUID userId, UUID courseId, EntitlementSource source, Instant at) {
    return jdbc.sql(
                """
                INSERT INTO enrollment (user_id, course_id, source, status, enrolled_at, status_at)
                VALUES (:user, :course, :source, 'ACTIVE', :at, :at)
                ON CONFLICT (user_id, course_id) DO UPDATE
                   SET status = 'ACTIVE', source = EXCLUDED.source,
                       status_at = EXCLUDED.status_at,
                       enrolled_at = LEAST(enrollment.enrolled_at, EXCLUDED.enrolled_at)
                 WHERE enrollment.status = 'REVOKED'
                   AND enrollment.status_at < EXCLUDED.status_at
                """)
            .param("user", userId)
            .param("course", courseId)
            .param("source", source.name())
            .param("at", Timestamp.from(at))
            .update()
        > 0;
  }

  /**
   * Two statements: end an ACTIVE, older enrollment (the counter's −1); if there was none, leave a
   * tombstone — {@code DO NOTHING} keeps any row that exists (a newer grant, an older revoke).
   */
  @Override
  public boolean revoke(UUID userId, UUID courseId, Instant at) {
    boolean ended =
        jdbc.sql(
                    """
                    UPDATE enrollment SET status = 'REVOKED', status_at = :at
                    WHERE user_id = :user AND course_id = :course
                      AND status = 'ACTIVE' AND status_at < :at
                    """)
                .param("at", Timestamp.from(at))
                .param("user", userId)
                .param("course", courseId)
                .update()
            > 0;
    if (!ended) {
      jdbc.sql(
              """
              INSERT INTO enrollment (user_id, course_id, status, enrolled_at, status_at)
              VALUES (:user, :course, 'REVOKED', :at, :at)
              ON CONFLICT (user_id, course_id) DO NOTHING
              """)
          .param("user", userId)
          .param("course", courseId)
          .param("at", Timestamp.from(at))
          .update();
    }
    return ended;
  }

  @Override
  public Set<UserCourse> activeAmong(Collection<UserCourse> pairs) {
    if (pairs.isEmpty()) {
      return Set.of();
    }
    List<UserCourse> list = List.copyOf(pairs);
    return new HashSet<>(
        jdbc.sql(
                """
                SELECT e.user_id, e.course_id
                FROM enrollment e
                JOIN unnest(:users, :courses) AS p (user_id, course_id)
                  ON p.user_id = e.user_id AND p.course_id = e.course_id
                WHERE e.status = 'ACTIVE'
                """)
            .param("users", SqlArrays.of("uuid", list.stream().map(UserCourse::userId).toArray()))
            .param(
                "courses", SqlArrays.of("uuid", list.stream().map(UserCourse::courseId).toArray()))
            .query(
                (rs, n) ->
                    new UserCourse(
                        rs.getObject("user_id", UUID.class), rs.getObject("course_id", UUID.class)))
            .list());
  }

  /**
   * ⭐ One statement for the whole batch, and a RECOUNT from the progress rows (not {@code +1}), so
   * running it twice gives the same numbers. The last lecture is the one with the newest heartbeat.
   */
  @Override
  public void refreshRollups(Collection<UserCourse> pairs) {
    if (pairs.isEmpty()) {
      return;
    }
    List<UserCourse> list = List.copyOf(pairs);
    jdbc.sql(
            """
            UPDATE enrollment e
            SET completed_lectures = s.completed,
                last_lecture_id = s.last_lecture,
                last_activity_at = s.last_at
            FROM (SELECT p.user_id, p.course_id,
                         count(*) FILTER (WHERE p.completed_at IS NOT NULL) AS completed,
                         (array_agg(p.lecture_id ORDER BY p.updated_at DESC))[1] AS last_lecture,
                         max(p.updated_at) AS last_at
                  FROM lecture_progress p
                  JOIN unnest(:users, :courses) AS k (user_id, course_id)
                    ON k.user_id = p.user_id AND k.course_id = p.course_id
                  GROUP BY p.user_id, p.course_id) s
            WHERE e.user_id = s.user_id AND e.course_id = s.course_id
            """)
        .param("users", SqlArrays.of("uuid", list.stream().map(UserCourse::userId).toArray()))
        .param("courses", SqlArrays.of("uuid", list.stream().map(UserCourse::courseId).toArray()))
        .update();
  }

  static Enrollment map(ResultSet rs) throws SQLException {
    return new Enrollment(
        rs.getObject("user_id", UUID.class),
        rs.getObject("course_id", UUID.class),
        Optional.ofNullable(rs.getString("source")).map(EntitlementSource::valueOf),
        EnrollmentStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("enrolled_at").toInstant(),
        rs.getTimestamp("status_at").toInstant(),
        rs.getInt("completed_lectures"),
        Optional.ofNullable(rs.getObject("last_lecture_id", UUID.class)),
        Optional.ofNullable(rs.getTimestamp("last_activity_at")).map(Timestamp::toInstant));
  }
}
