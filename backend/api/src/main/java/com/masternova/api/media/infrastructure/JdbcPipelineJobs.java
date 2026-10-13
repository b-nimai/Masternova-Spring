package com.masternova.api.media.infrastructure;

import com.masternova.api.media.domain.MediaStatus;
import com.masternova.api.media.domain.PipelineJobs;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link PipelineJobs} over JDBC: {@code pipeline_job} is the worker's table, not an entity of this
 * module. ⭐ Every write is conditional on the current status, like the worker's own.
 */
@Repository
class JdbcPipelineJobs implements PipelineJobs {

  private static final RowMapper<DeadJob> ROW =
      (rs, i) ->
          new DeadJob(
              rs.getObject("id", UUID.class),
              rs.getObject("asset_id", UUID.class),
              rs.getString("type"),
              rs.getString("rung"),
              rs.getInt("attempts"),
              rs.getString("last_error"),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcClient jdbc;

  JdbcPipelineJobs(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<JobProgress> progressOf(UUID assetId) {
    return jdbc.sql(
            """
            SELECT type, rung, status, progress FROM pipeline_job WHERE asset_id = :asset
             ORDER BY CASE type WHEN 'PROBE' THEN 0 WHEN 'TRANSCODE' THEN 1
                                WHEN 'THUMBNAILS' THEN 2 ELSE 3 END,
                      length(rung), rung
            """)
        .param("asset", assetId)
        .query(
            (rs, i) -> {
              String status = rs.getString("status");
              int percent =
                  switch (status) {
                    case "DONE" -> 100;
                    case "RUNNING" -> rs.getInt("progress"); // NULL → 0
                    default -> 0;
                  };
              return new JobProgress(rs.getString("type"), rs.getString("rung"), status, percent);
            })
        .list();
  }

  @Override
  public List<DeadJob> list(int limit) {
    return jdbc.sql(
            """
            SELECT id, asset_id, type, rung, attempts, last_error, updated_at FROM pipeline_job
             WHERE status = 'DEAD' ORDER BY updated_at DESC LIMIT :limit
            """)
        .param("limit", limit)
        .query(ROW)
        .list();
  }

  @Override
  public Optional<String> statusOf(UUID jobId) {
    return jdbc.sql("SELECT status FROM pipeline_job WHERE id = :id")
        .param("id", jobId)
        .query(String.class)
        .optional();
  }

  @Override
  public Optional<DeadJob> find(UUID jobId) {
    return jdbc.sql(
            """
            SELECT id, asset_id, type, rung, attempts, last_error, updated_at FROM pipeline_job
             WHERE id = :id
            """)
        .param("id", jobId)
        .query(ROW)
        .optional();
  }

  @Override
  public boolean requeue(UUID jobId, Instant now) {
    return jdbc.sql(
                """
                UPDATE pipeline_job
                   SET status = 'PENDING', attempts = 0, next_attempt_at = :now, worker_id = NULL,
                       updated_at = :now
                 WHERE id = :id AND status = 'DEAD'
                """)
            .param("now", Timestamp.from(now))
            .param("id", jobId)
            .update()
        == 1;
  }

  @Override
  public void reviveAsset(UUID assetId, MediaStatus to) {
    jdbc.sql(
            """
            UPDATE media_asset SET status = :to, failure_code = NULL, version = version + 1
             WHERE id = :id AND status = 'FAILED'
            """)
        .param("to", to.name())
        .param("id", assetId)
        .update();
  }
}
