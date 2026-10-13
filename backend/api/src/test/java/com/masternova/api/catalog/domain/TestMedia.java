package com.masternova.api.catalog.domain;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Makes media agree that a course's videos are READY: one {@code media_asset} row per VIDEO
 * lecture's asset id, owned by the course's instructor — what the upload pipeline leaves behind.
 * Plain SQL, like {@link TestInstructors}: catalog's tests don't reach into media's internals.
 */
public final class TestMedia {

  private final JdbcClient jdbc;

  public TestMedia(DataSource dataSource) {
    this.jdbc = JdbcClient.create(dataSource);
  }

  public Course readyVideos(Course course) {
    for (UUID asset : course.videoAssetIds()) {
      jdbc.sql(
              """
              INSERT INTO media_asset (id, owner_id, kind, status, original_filename, content_type,
                                       size_bytes, duration_seconds, created_at, ready_at, version)
              VALUES (:id, :owner, 'VIDEO', 'READY', 'v.mp4', 'video/mp4', 1, 75, now(), now(), 0)
              ON CONFLICT (id) DO NOTHING
              """)
          .param("id", asset)
          .param("owner", course.instructor().id())
          .update();
    }
    return course;
  }

  /** One asset row, as media leaves it in {@code status} — the only place tests insert one. */
  public UUID asset(UUID ownerId, String status, Integer durationSeconds) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO media_asset (id, owner_id, kind, status, original_filename, content_type,
                                     size_bytes, duration_seconds, created_at, version)
            VALUES (:id, :owner, 'VIDEO', :status, 'v.mp4', 'video/mp4', 1, :seconds, now(), 0)
            """)
        .param("id", id)
        .param("owner", ownerId)
        .param("status", status)
        .param("seconds", durationSeconds)
        .update();
    return id;
  }

  /** Before {@link TestInstructors#deleteAll()}: assets reference their owners. */
  public void deleteAll() {
    jdbc.sql("DELETE FROM pipeline_job").update();
    jdbc.sql("DELETE FROM upload_session").update();
    jdbc.sql("DELETE FROM media_rendition").update();
    jdbc.sql("DELETE FROM media_asset").update();
  }
}
