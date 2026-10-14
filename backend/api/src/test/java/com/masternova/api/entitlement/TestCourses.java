package com.masternova.api.entitlement;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A course with one preview and one paid video lecture, inserted with plain SQL — entitlement's
 * tests don't build catalog's aggregate (like {@code TestInstructors} for users). Users come from
 * {@code TestInstructors}; {@code TestInstructors.deleteAll()} cleans up both.
 */
public final class TestCourses {

  /** What a test needs to point at. */
  public record Seeded(UUID courseId, UUID previewLecture, UUID paidLecture) {}

  private final JdbcClient jdbc;

  public TestCourses(DataSource dataSource) {
    this.jdbc = JdbcClient.create(dataSource);
  }

  /**
   * @param status DRAFT, IN_REVIEW, PUBLISHED or ARCHIVED
   * @param priceMinor with {@code priced} false, the course has never been priced
   * @param assetId both lectures' video, or {@code null}
   */
  public Seeded create(
      UUID instructorId, String status, long priceMinor, boolean priced, UUID assetId) {
    UUID course = UUID.randomUUID();
    UUID section = UUID.randomUUID();
    Seeded seeded = new Seeded(course, UUID.randomUUID(), UUID.randomUUID());
    jdbc.sql(
            """
            INSERT INTO course (id, slug, title, description, language, level, status, published_at,
                                price_minor, currency, price_set_at, instructor_id, instructor_name,
                                category_id, lecture_count, total_duration_seconds, created_at,
                                updated_at, version)
            VALUES (:id, :slug, 'Entitlement test', 'A course.', 'en', 'BEGINNER', :status,
                    CASE WHEN :status IN ('PUBLISHED', 'ARCHIVED') THEN now() END,
                    :price, 'INR', CASE WHEN :priced THEN now() END, :instructor, 'Instructor',
                    (SELECT id FROM category WHERE parent_id IS NOT NULL ORDER BY slug LIMIT 1),
                    2, 120, now(), now(), 0)
            """)
        .param("id", course)
        .param("slug", "ent-" + course)
        .param("status", status)
        .param("price", priceMinor)
        .param("priced", priced)
        .param("instructor", instructorId)
        .update();
    jdbc.sql("INSERT INTO section (id, course_id, title, position) VALUES (:id, :course, 'S', 0)")
        .param("id", section)
        .param("course", course)
        .update();
    lecture(seeded.previewLecture(), section, 0, true, assetId);
    lecture(seeded.paidLecture(), section, 1, false, assetId);
    return seeded;
  }

  private void lecture(UUID id, UUID section, int position, boolean preview, UUID assetId) {
    jdbc.sql(
            """
            INSERT INTO lecture (id, section_id, title, kind, position, preview, duration_seconds,
                                 asset_id)
            VALUES (:id, :section, 'L', 'VIDEO', :position, :preview, 60, :asset)
            """)
        .param("id", id)
        .param("section", section)
        .param("position", position)
        .param("preview", preview)
        .param("asset", assetId, java.sql.Types.OTHER)
        .update();
  }
}
