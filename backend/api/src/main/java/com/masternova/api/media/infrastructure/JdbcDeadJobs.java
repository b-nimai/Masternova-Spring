package com.masternova.api.media.infrastructure;

import com.masternova.api.media.domain.DeadJobs;
import com.masternova.api.media.domain.MediaStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DeadJobs} over JDBC: {@code pipeline_job} is the worker's table, not an entity of this
 * module. ⭐ Every write is conditional on the current status, like the worker's own.
 */
@Repository
class JdbcDeadJobs implements DeadJobs {

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

  JdbcDeadJobs(JdbcClient jdbc) {
    this.jdbc = jdbc;
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
