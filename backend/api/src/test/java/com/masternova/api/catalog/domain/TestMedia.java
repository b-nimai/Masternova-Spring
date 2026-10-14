package com.masternova.api.catalog.domain;

import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
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

  /**
   * What the worker's pipeline leaves in storage for a READY asset: a master with one 240p rung,
   * its playlist (init segment + one segment), and that segment's bytes ({@code segment-bytes}).
   */
  public static void storeHls(ObjectStorage storage, UUID asset) {
    String hls = "application/vnd.apple.mpegurl";
    storage.putText(
        MediaKeys.master(asset),
        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=385000,RESOLUTION=426x240\n240p/index.m3u8\n",
        hls);
    storage.putText(
        MediaKeys.rungPlaylist(asset, "240p"),
        "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:6.0,\nseg_00000.m4s\n#EXT-X-ENDLIST\n",
        hls);
    storage.putText(MediaKeys.segment(asset, "240p", 0), "segment-bytes", "video/iso.segment");
  }

  /** Before {@link TestInstructors#deleteAll()}: assets reference their owners. */
  public void deleteAll() {
    jdbc.sql("DELETE FROM pipeline_job").update();
    jdbc.sql("DELETE FROM upload_session").update();
    jdbc.sql("DELETE FROM media_rendition").update();
    jdbc.sql("DELETE FROM media_asset").update();
  }
}
