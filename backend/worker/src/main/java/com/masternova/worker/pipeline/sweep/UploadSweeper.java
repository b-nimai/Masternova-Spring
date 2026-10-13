package com.masternova.worker.pipeline.sweep;

import com.masternova.storage.ObjectStorage;
import com.masternova.storage.PendingUpload;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cleans up uploads nobody will finish (media LLD §8). S3 bills for the parts of an open multipart
 * upload until it is completed or aborted, and an instructor who closes the tab for good never does
 * either.
 *
 * <ol>
 *   <li>OPEN sessions past {@code expires_at}: abort at the provider, then {@code EXPIRED} + the
 *       asset {@code FAILED (UPLOAD_EXPIRED)} — conditional updates, so a sweep racing the user's
 *       last-second complete loses cleanly;
 *   <li>provider uploads with NO open session, older than a day: created by an api that crashed
 *       between {@code CreateMultipartUpload} and its insert (media LLD §9). Abort.
 * </ol>
 *
 * Every step is idempotent (aborting twice is fine), and ShedLock keeps replicas from sweeping at
 * the same time.
 */
@Component
public class UploadSweeper {

  private static final Logger log = LoggerFactory.getLogger(UploadSweeper.class);
  static final Duration ORPHAN_AGE = Duration.ofHours(24);

  private final JdbcClient jdbc;
  private final ObjectStorage storage;
  private final Clock clock;

  UploadSweeper(JdbcClient jdbc, ObjectStorage storage, Clock clock) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.clock = clock;
  }

  @Scheduled(cron = "${masternova.pipeline.sweep-cron:0 */15 * * * *}")
  @SchedulerLock(name = "upload-sweeper", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void sweep() {
    int expired = expireSessions();
    int orphans = abortOrphans();
    if (expired + orphans > 0) {
      log.info("upload sweep: {} expired sessions, {} orphan provider uploads", expired, orphans);
    }
  }

  record Expired(UUID id, UUID assetId, String key, String uploadId) {}

  int expireSessions() {
    List<Expired> due =
        jdbc.sql(
                """
                SELECT id, asset_id, object_key, provider_upload_id FROM upload_session
                 WHERE status = 'OPEN' AND expires_at <= :now ORDER BY expires_at LIMIT 100
                """)
            .param("now", Timestamp.from(clock.instant()))
            .query(
                (rs, i) ->
                    new Expired(
                        rs.getObject("id", UUID.class),
                        rs.getObject("asset_id", UUID.class),
                        rs.getString("object_key"),
                        rs.getString("provider_upload_id")))
            .list();
    for (Expired s : due) {
      storage.abortMultipartUpload(
          s.key(), s.uploadId()); // first: the bill stops even if we die next
      jdbc.sql(
              "UPDATE upload_session SET status = 'EXPIRED', version = version + 1 WHERE id = :id AND status = 'OPEN'")
          .param("id", s.id())
          .update();
      jdbc.sql(
              """
              UPDATE media_asset SET status = 'FAILED', failure_code = 'UPLOAD_EXPIRED', version = version + 1
               WHERE id = :id AND status = 'AWAITING_UPLOAD'
              """)
          .param("id", s.assetId())
          .update();
    }
    return due.size();
  }

  int abortOrphans() {
    Set<String> open =
        new HashSet<>(
            jdbc.sql("SELECT provider_upload_id FROM upload_session WHERE status = 'OPEN'")
                .query(String.class)
                .list());
    Instant cutoff = clock.instant().minus(ORPHAN_AGE);
    int aborted = 0;
    for (PendingUpload upload : storage.listMultipartUploads("media/")) {
      if (!open.contains(upload.uploadId()) && upload.initiated().isBefore(cutoff)) {
        storage.abortMultipartUpload(upload.key(), upload.uploadId());
        aborted++;
      }
    }
    return aborted;
  }
}
