package com.masternova.api.engagement.infrastructure;

import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.engagement.domain.Review;
import com.masternova.api.engagement.domain.ReviewRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reviews and tallies in plain SQL: the lock, the keyset page and the recount are the point. */
@Repository
class JdbcReviewRepository implements ReviewRepository {

  private static final String REVIEW_COLUMNS =
      "id, course_id, user_id, rating, body, author_name, created_at, updated_at";
  private static final String STARS = "stars_1, stars_2, stars_3, stars_4, stars_5";

  /** ⭐ The stars of a course's reviews, counted in ONE pass: a filtered aggregate per star. */
  private static final String RECOUNT =
      """
      count(*) FILTER (WHERE rating = 1)::int AS stars_1,
      count(*) FILTER (WHERE rating = 2)::int AS stars_2,
      count(*) FILTER (WHERE rating = 3)::int AS stars_3,
      count(*) FILTER (WHERE rating = 4)::int AS stars_4,
      count(*) FILTER (WHERE rating = 5)::int AS stars_5
      """;

  private final JdbcClient jdbc;

  JdbcReviewRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public RatingTally lockTally(UUID courseId) {
    return selectForUpdate(courseId)
        .orElseGet(
            () -> {
              // ⭐ a first review has no row to lock: make one. DO NOTHING keeps a concurrent
              //    creator's row, and both then queue on the SELECT … FOR UPDATE
              jdbc.sql(
                      "INSERT INTO course_rating (course_id) VALUES (:course) ON CONFLICT DO NOTHING")
                  .param("course", courseId)
                  .update();
              return selectForUpdate(courseId).orElseThrow();
            });
  }

  private Optional<RatingTally> selectForUpdate(UUID courseId) {
    return jdbc.sql("SELECT " + STARS + " FROM course_rating WHERE course_id = :course FOR UPDATE")
        .param("course", courseId)
        .query((rs, n) -> tally(rs))
        .optional();
  }

  @Override
  public void saveTally(UUID courseId, RatingTally tally) {
    jdbc.sql(
            """
            UPDATE course_rating
               SET stars_1 = :s1, stars_2 = :s2, stars_3 = :s3, stars_4 = :s4, stars_5 = :s5
             WHERE course_id = :course
            """)
        .param("s1", tally.starsOf(1))
        .param("s2", tally.starsOf(2))
        .param("s3", tally.starsOf(3))
        .param("s4", tally.starsOf(4))
        .param("s5", tally.starsOf(5))
        .param("course", courseId)
        .update();
  }

  @Override
  public Optional<RatingTally> tally(UUID courseId) {
    return jdbc.sql("SELECT " + STARS + " FROM course_rating WHERE course_id = :course")
        .param("course", courseId)
        .query((rs, n) -> tally(rs))
        .optional();
  }

  @Override
  public Optional<Review> find(UUID courseId, UUID userId) {
    return jdbc.sql(
            "SELECT "
                + REVIEW_COLUMNS
                + " FROM review WHERE course_id = :course AND user_id = :user")
        .param("course", courseId)
        .param("user", userId)
        .query((rs, n) -> review(rs))
        .optional();
  }

  /** An upsert on the one-each key: the caller holds the tally lock, so it never races itself. */
  @Override
  public void save(Review review) {
    jdbc.sql(
            """
            INSERT INTO review (id, course_id, user_id, rating, body, author_name,
                                created_at, updated_at)
            VALUES (:id, :course, :user, :rating, :body, :author, :created, :updated)
            ON CONFLICT (course_id, user_id) DO UPDATE
               SET rating = EXCLUDED.rating, body = EXCLUDED.body,
                   author_name = EXCLUDED.author_name, updated_at = EXCLUDED.updated_at
            """)
        .param("id", review.id())
        .param("course", review.courseId())
        .param("user", review.userId())
        .param("rating", review.rating())
        .param("body", review.body())
        .param("author", review.authorName())
        .param("created", Timestamp.from(review.createdAt()))
        .param("updated", Timestamp.from(review.updatedAt()))
        .update();
  }

  @Override
  public Optional<Integer> delete(UUID courseId, UUID userId) {
    return jdbc.sql(
            "DELETE FROM review WHERE course_id = :course AND user_id = :user RETURNING rating")
        .param("course", courseId)
        .param("user", userId)
        .query(Integer.class)
        .optional();
  }

  /**
   * ⭐ A row comparison {@code (created_at, id) < (:at, :id)}: Postgres starts the scan of {@code
   * review_course_newest_idx} AT the cursor (catalog needed a redundant bound because JPA can't
   * write this; plain SQL can).
   */
  @Override
  public List<Review> page(UUID courseId, Instant beforeAt, UUID beforeId, int limit) {
    String after = beforeAt == null ? "" : " AND (created_at, id) < (:at, :id)";
    var sql =
        jdbc.sql(
                "SELECT "
                    + REVIEW_COLUMNS
                    + " FROM review WHERE course_id = :course"
                    + after
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit")
            .param("course", courseId)
            .param("limit", limit);
    if (beforeAt != null) {
      sql = sql.param("at", Timestamp.from(beforeAt)).param("id", beforeId);
    }
    return sql.query((rs, n) -> review(rs)).list();
  }

  /**
   * ⭐ A FULL JOIN of the stored tallies with a recount: a tally whose reviews all vanished (no
   * recount row) and reviews with no tally (none, unless someone inserted around the code) both
   * show up as differences.
   */
  @Override
  public List<UUID> drifted() {
    return jdbc.sql(
            """
            WITH truth AS (SELECT course_id, %s FROM review GROUP BY course_id)
            SELECT COALESCE(t.course_id, r.course_id) AS course_id
            FROM course_rating r FULL JOIN truth t ON t.course_id = r.course_id
            WHERE (r.stars_1, r.stars_2, r.stars_3, r.stars_4, r.stars_5)
                  IS DISTINCT FROM
                  (COALESCE(t.stars_1, 0), COALESCE(t.stars_2, 0), COALESCE(t.stars_3, 0),
                   COALESCE(t.stars_4, 0), COALESCE(t.stars_5, 0))
            """
                .formatted(RECOUNT))
        .query((rs, n) -> rs.getObject("course_id", UUID.class))
        .list();
  }

  @Override
  public RatingTally recount(UUID courseId) {
    return jdbc.sql("SELECT " + RECOUNT + " FROM review WHERE course_id = :course")
        .param("course", courseId)
        .query((rs, n) -> tally(rs))
        .single();
  }

  @Override
  public List<CourseTally> lockTallies(UUID afterCourseId, int limit) {
    String after = afterCourseId == null ? "" : " WHERE course_id > :after";
    var sql =
        jdbc.sql(
                "SELECT course_id, "
                    + STARS
                    + " FROM course_rating"
                    + after
                    + " ORDER BY course_id LIMIT :limit FOR UPDATE")
            .param("limit", limit);
    if (afterCourseId != null) {
      sql = sql.param("after", afterCourseId);
    }
    return sql.query((rs, n) -> new CourseTally(rs.getObject("course_id", UUID.class), tally(rs)))
        .list();
  }

  @Override
  public List<UUID> withoutTally(List<UUID> courseIds) {
    if (courseIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            SELECT c.id FROM unnest(:ids) AS c(id)
            WHERE NOT EXISTS (SELECT 1 FROM course_rating r WHERE r.course_id = c.id)
            """)
        .param("ids", courseIds.toArray(UUID[]::new))
        .query((rs, n) -> rs.getObject("id", UUID.class))
        .list();
  }

  private static RatingTally tally(ResultSet rs) throws SQLException {
    return RatingTally.of(
        rs.getInt("stars_1"),
        rs.getInt("stars_2"),
        rs.getInt("stars_3"),
        rs.getInt("stars_4"),
        rs.getInt("stars_5"));
  }

  private static Review review(ResultSet rs) throws SQLException {
    return new Review(
        rs.getObject("id", UUID.class),
        rs.getObject("course_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getInt("rating"),
        rs.getString("body"),
        rs.getString("author_name"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
