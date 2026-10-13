package com.masternova.worker.pipeline;

import com.masternova.worker.pipeline.ffmpeg.SourceInfo;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The worker's writes to media's tables (media LLD §9, "who writes what"). ⭐ Every status change is
 * CONDITIONAL ({@code WHERE status IN (…)}): a stale or duplicate job changes 0 rows instead of
 * moving an asset backwards. Renditions are UPSERTED on their primary key, so a rerun replaces,
 * never adds (7.10).
 */
@Repository
public class PipelineStore {

  private final JdbcClient jdbc;
  private final Clock clock;

  PipelineStore(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** {@code SELECT … FOR UPDATE} on the asset: serialises the completions of its jobs (fan-in). */
  public void lockAsset(UUID assetId) {
    jdbc.sql("SELECT id FROM media_asset WHERE id = :id FOR UPDATE")
        .param("id", assetId)
        .query(UUID.class)
        .optional();
  }

  /** UPLOADED → PROCESSING with what the probe found (a PROBE rerun refreshes the same values). */
  public void markProcessing(UUID assetId, SourceInfo info) {
    jdbc.sql(
            """
            UPDATE media_asset
               SET status = 'PROCESSING', duration_seconds = :duration, width = :width,
                   height = :height, has_audio = :audio, version = version + 1
             WHERE id = :id AND status IN ('UPLOADED', 'PROCESSING')
            """)
        .param("duration", (int) Math.ceil(info.durationSeconds()))
        .param("width", info.width())
        .param("height", info.height())
        .param("audio", info.hasAudio())
        .param("id", assetId)
        .update();
  }

  public boolean markReady(UUID assetId) {
    return jdbc.sql(
                """
                UPDATE media_asset SET status = 'READY', ready_at = :now, version = version + 1
                 WHERE id = :id AND status = 'PROCESSING'
                """)
            .param("now", Timestamp.from(clock.instant()))
            .param("id", assetId)
            .update()
        == 1;
  }

  public void markFailed(UUID assetId, String code) {
    jdbc.sql(
            """
            UPDATE media_asset SET status = 'FAILED', failure_code = :code, version = version + 1
             WHERE id = :id AND status IN ('UPLOADED', 'PROCESSING')
            """)
        .param("code", code)
        .param("id", assetId)
        .update();
  }

  /** The probe's findings, for the jobs after it. Empty until the probe has run. */
  public Optional<SourceInfo> sourceInfo(UUID assetId) {
    return jdbc.sql(
            """
            SELECT duration_seconds, width, height, has_audio FROM media_asset
             WHERE id = :id AND duration_seconds IS NOT NULL
            """)
        .param("id", assetId)
        .query(
            (rs, i) ->
                new SourceInfo(
                    rs.getInt("duration_seconds"),
                    rs.getInt("width"),
                    rs.getInt("height"),
                    rs.getBoolean("has_audio")))
        .optional();
  }

  /** ⭐ Upsert on (asset_id, rung): a rerun of a rung replaces its row. */
  public void upsertRendition(UUID assetId, Rendition r) {
    jdbc.sql(
            """
            INSERT INTO media_rendition (asset_id, rung, width, height, bandwidth, playlist_key,
                                         segment_count, created_at)
            VALUES (:asset, :rung, :width, :height, :bandwidth, :key, :segments, :now)
            ON CONFLICT (asset_id, rung) DO UPDATE
               SET width = EXCLUDED.width, height = EXCLUDED.height, bandwidth = EXCLUDED.bandwidth,
                   playlist_key = EXCLUDED.playlist_key, segment_count = EXCLUDED.segment_count
            """)
        .param("asset", assetId)
        .param("rung", r.rung())
        .param("width", r.width())
        .param("height", r.height())
        .param("bandwidth", r.bandwidth())
        .param("key", r.playlistKey())
        .param("segments", r.segmentCount())
        .param("now", Timestamp.from(clock.instant()))
        .update();
  }

  /** The asset's renditions, lowest first — what the master playlist lists. */
  public List<Rendition> renditions(UUID assetId) {
    return jdbc.sql(
            """
            SELECT rung, width, height, bandwidth, playlist_key, segment_count
              FROM media_rendition WHERE asset_id = :asset ORDER BY height
            """)
        .param("asset", assetId)
        .query(
            (rs, i) ->
                new Rendition(
                    rs.getString("rung"),
                    rs.getInt("width"),
                    rs.getInt("height"),
                    rs.getInt("bandwidth"),
                    rs.getString("playlist_key"),
                    rs.getInt("segment_count")))
        .list();
  }

  public record Rendition(
      String rung, int width, int height, int bandwidth, String playlistKey, int segmentCount) {}
}
