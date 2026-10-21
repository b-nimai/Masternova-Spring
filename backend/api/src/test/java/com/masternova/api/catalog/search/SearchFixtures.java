package com.masternova.api.catalog.search;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Courses with chosen text, inserted with plain SQL (search reads only the row's columns). */
final class SearchFixtures {

  private final JdbcClient jdbc;
  private final UUID instructor;

  SearchFixtures(DataSource dataSource, UUID instructor) {
    this.jdbc = JdbcClient.create(dataSource);
    this.instructor = instructor;
  }

  UUID published(String title, String subtitle, String description) {
    return course(title, subtitle, description, "en", "PUBLISHED");
  }

  UUID course(String title, String subtitle, String description, String language, String status) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO course (id, slug, title, subtitle, description, language, level, status,
                                published_at, price_minor, currency, instructor_id, instructor_name,
                                category_id, lecture_count, total_duration_seconds, created_at,
                                updated_at, version)
            VALUES (:id, :slug, :title, :subtitle, :description, :language, 'BEGINNER', :status,
                    CASE WHEN :status = 'PUBLISHED' THEN now() END, 0, 'INR', :instructor,
                    'Instructor',
                    (SELECT id FROM category WHERE parent_id IS NOT NULL ORDER BY slug LIMIT 1),
                    0, 0, now(), now(), 0)
            """)
        .param("id", id)
        .param("slug", "s-" + id)
        .param("title", title)
        .param("subtitle", subtitle)
        .param("description", description)
        .param("language", language)
        .param("status", status)
        .param("instructor", instructor)
        .update();
    return id;
  }

  void edit(UUID id, String description) {
    jdbc.sql("UPDATE course SET description = :d WHERE id = :id")
        .param("d", description)
        .param("id", id)
        .update();
  }
}
