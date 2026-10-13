package com.masternova.worker.pipeline;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The rows a pipeline needs before it can start — an owner and an UPLOADED asset — inserted with
 * plain SQL, exactly as the api leaves them after a complete (media LLD §5).
 */
public final class TestAssets {

  private final JdbcClient jdbc;

  public TestAssets(DataSource dataSource) {
    this.jdbc = JdbcClient.create(dataSource);
  }

  public UUID uploadedAsset() {
    UUID owner = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO app_user (id, email, display_name, password_hash, created_at, version)"
                + " VALUES (:id, :email, 'Pipeline Test', '{noop}x', now(), 0)")
        .param("id", owner)
        .param("email", owner + "@pipeline.test")
        .update();
    UUID asset = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO media_asset (id, owner_id, kind, status, original_filename, content_type,
                                     size_bytes, created_at, version)
            VALUES (:id, :owner, 'VIDEO', 'UPLOADED', 'lecture.mp4', 'video/mp4', 1, now(), 0)
            """)
        .param("id", asset)
        .param("owner", owner)
        .update();
    return asset;
  }

  public String status(UUID asset) {
    return jdbc.sql("SELECT status FROM media_asset WHERE id = :id")
        .param("id", asset)
        .query(String.class)
        .single();
  }

  public void deleteAll() {
    jdbc.sql("DELETE FROM pipeline_job").update();
    jdbc.sql("DELETE FROM media_rendition").update();
    jdbc.sql("DELETE FROM upload_session").update();
    jdbc.sql("DELETE FROM media_asset").update();
    jdbc.sql("DELETE FROM app_user WHERE email LIKE '%@pipeline.test'").update();
  }
}
