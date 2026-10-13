package com.masternova.worker.pipeline.job;

import com.masternova.worker.pipeline.PipelineProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code pipeline_job} over JDBC — the outbox's claim/lease protocol, for long jobs. Every write
 * that ends a run is CONDITIONAL on still holding the lease ({@code worker_id = me AND status =
 * RUNNING}): a worker that lost its lease (paused, partitioned) and finishes late changes nothing.
 */
@Repository
public class JobRepository {

  private final JdbcClient jdbc;
  private final Clock clock;
  private final int maxAttempts;

  JobRepository(JdbcClient jdbc, Clock clock, PipelineProperties props) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.maxAttempts = props.maxAttempts();
  }

  /** ⭐ Idempotent: the unique (asset, type, rung) makes a second enqueue a no-op. */
  public boolean enqueue(JobSpec spec) {
    Timestamp now = Timestamp.from(clock.instant());
    return jdbc.sql(
                """
                INSERT INTO pipeline_job (id, asset_id, type, rung, status, attempts, next_attempt_at,
                                          created_at, updated_at)
                VALUES (:id, :asset, :type, :rung, 'PENDING', 0, :now, :now, :now)
                ON CONFLICT (asset_id, type, rung) DO NOTHING
                """)
            .param("id", UUID.randomUUID())
            .param("asset", spec.assetId())
            .param("type", spec.type().name())
            .param("rung", spec.rung())
            .param("now", now)
            .update()
        == 1;
  }

  /**
   * Claims up to {@code max} due jobs: PENDING whose time has come, or RUNNING whose lease lapsed
   * (its worker died). {@code FOR UPDATE SKIP LOCKED}: N workers claim side by side, never the same
   * row, without waiting on each other.
   */
  public List<Job> claim(int max, Duration lease, String workerId) {
    Instant now = clock.instant();
    return jdbc.sql(
            """
            UPDATE pipeline_job
               SET status = 'RUNNING', attempts = attempts + 1, worker_id = :worker,
                   next_attempt_at = :leaseUntil, updated_at = :now
             WHERE id IN (SELECT id FROM pipeline_job
                           WHERE status IN ('PENDING', 'RUNNING') AND next_attempt_at <= :now
                             -- a lapsed lease on the LAST attempt is buried, never re-handed out
                             AND (status = 'PENDING' OR attempts < :maxAttempts)
                           ORDER BY next_attempt_at
                           LIMIT :max
                           FOR UPDATE SKIP LOCKED)
            RETURNING id, asset_id, type, rung, attempts
            """)
        .param("worker", workerId)
        .param("leaseUntil", Timestamp.from(now.plus(lease)))
        .param("now", Timestamp.from(now))
        .param("max", max)
        .param("maxAttempts", maxAttempts)
        .query(
            (rs, i) ->
                new Job(
                    rs.getObject("id", UUID.class),
                    rs.getObject("asset_id", UUID.class),
                    JobType.valueOf(rs.getString("type")),
                    rs.getString("rung"),
                    rs.getInt("attempts")))
        .list();
  }

  /** Extends the lease while the job runs. {@code false} = the lease was lost: stop caring. */
  public boolean heartbeat(UUID jobId, String workerId, Duration lease, Integer progress) {
    Instant now = clock.instant();
    return jdbc.sql(
                """
                UPDATE pipeline_job SET next_attempt_at = :leaseUntil, progress = COALESCE(:progress, progress),
                                        updated_at = :now
                 WHERE id = :id AND worker_id = :worker AND status = 'RUNNING'
                """)
            .param("leaseUntil", Timestamp.from(now.plus(lease)))
            .param("progress", progress)
            .param("now", Timestamp.from(now))
            .param("id", jobId)
            .param("worker", workerId)
            .update()
        == 1;
  }

  /** RUNNING → PENDING with the attempt NOT counted: this process is shutting down. */
  public boolean release(UUID jobId, String workerId) {
    return jdbc.sql(
                """
                UPDATE pipeline_job
                   SET status = 'PENDING', attempts = attempts - 1, next_attempt_at = :now,
                       worker_id = NULL, updated_at = :now
                 WHERE id = :id AND worker_id = :worker AND status = 'RUNNING'
                """)
            .param("now", Timestamp.from(clock.instant()))
            .param("id", jobId)
            .param("worker", workerId)
            .update()
        == 1;
  }

  /**
   * ⭐ A job whose worker DIED (lease lapsed) on its last allowed attempt is buried, not claimed
   * again: a source that OOM-kills every worker would otherwise take them down forever, because the
   * attempt limit in {@code JobRunner.fail} never runs after a SIGKILL (found in review).
   *
   * @return the assets of the jobs buried
   */
  public List<UUID> buryAbandoned(int maxAttempts) {
    Instant now = clock.instant();
    return jdbc.sql(
            """
            UPDATE pipeline_job
               SET status = 'DEAD', updated_at = :now,
                   last_error = 'the worker was lost on every one of ' || attempts || ' attempts'
             WHERE status = 'RUNNING' AND next_attempt_at <= :now AND attempts >= :max
            RETURNING asset_id
            """)
        .param("now", Timestamp.from(now))
        .param("max", maxAttempts)
        .query(UUID.class)
        .list();
  }

  /** Is this job running right now, under a lease that hasn't lapsed? (the startup cleanup) */
  public boolean isLeased(UUID jobId) {
    return jdbc.sql(
                "SELECT count(*) FROM pipeline_job WHERE id = :id AND status = 'RUNNING'"
                    + " AND next_attempt_at > :now")
            .param("id", jobId)
            .param("now", Timestamp.from(clock.instant()))
            .query(Integer.class)
            .single()
        > 0;
  }

  /** RUNNING → DONE, only while still holding the lease. */
  public boolean complete(UUID jobId, String workerId) {
    return finish(jobId, workerId, "DONE", null, clock.instant(), 100);
  }

  /** RUNNING → PENDING at {@code runAfter} (a temporary failure). */
  public boolean reschedule(UUID jobId, String workerId, Instant runAfter, String error) {
    return finish(jobId, workerId, "PENDING", error, runAfter, null);
  }

  /** RUNNING → DEAD (permanent failure, or attempts exhausted). */
  public boolean bury(UUID jobId, String workerId, String error) {
    return finish(jobId, workerId, "DEAD", error, clock.instant(), null);
  }

  private boolean finish(
      UUID jobId, String workerId, String status, String error, Instant next, Integer progress) {
    return jdbc.sql(
                """
                UPDATE pipeline_job
                   SET status = :status, last_error = :error, next_attempt_at = :next,
                       progress = COALESCE(:progress, progress), updated_at = :now
                 WHERE id = :id AND worker_id = :worker AND status = 'RUNNING'
                """)
            .param("status", status)
            .param("error", error)
            .param("next", Timestamp.from(next))
            .param("progress", progress)
            .param("now", Timestamp.from(clock.instant()))
            .param("id", jobId)
            .param("worker", workerId)
            .update()
        == 1;
  }

  /** Every job of an asset and its status — what fan-in decides from. */
  public List<JobState> jobsOf(UUID assetId) {
    return jdbc.sql("SELECT type, rung, status FROM pipeline_job WHERE asset_id = :asset")
        .param("asset", assetId)
        .query(
            (rs, i) ->
                new JobState(
                    JobType.valueOf(rs.getString("type")),
                    rs.getString("rung"),
                    rs.getString("status")))
        .list();
  }

  public record JobState(JobType type, String rung, String status) {
    public boolean done() {
      return status.equals("DONE");
    }
  }
}
