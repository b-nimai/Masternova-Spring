package com.masternova.worker.pipeline.sweep;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.storage.PendingUpload;
import com.masternova.worker.MutableClock;
import com.masternova.worker.TestcontainersConfiguration;
import com.masternova.worker.pipeline.TestAssets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Abandoned uploads are aborted at the provider (media LLD §8), on real SeaweedFS + Postgres. The
 * clock is moved forward instead of waiting a day.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, UploadSweeperIT.Config.class})
class UploadSweeperIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired UploadSweeper sweeper;
  @Autowired ObjectStorage storage;
  @Autowired MutableClock clock;
  @Autowired DataSource dataSource;
  JdbcClient jdbc;
  TestAssets assets;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    assets = new TestAssets(dataSource);
    assets.deleteAll();
    jdbc.sql("DELETE FROM shedlock").update();
    clock.reset();
    clock.advance(Duration.between(MutableClock.T0, Instant.now())); // "now" = the real now
    for (PendingUpload u : storage.listMultipartUploads("media/")) {
      storage.abortMultipartUpload(u.key(), u.uploadId());
    }
  }

  @AfterEach
  void tearDown() {
    assets.deleteAll();
  }

  /** An instructor's upload: a real provider upload + an OPEN session expiring at {@code at}. */
  private UUID openSession(Instant expiresAt) {
    UUID asset = assets.uploadedAsset();
    jdbc.sql("UPDATE media_asset SET status = 'AWAITING_UPLOAD' WHERE id = :id")
        .param("id", asset)
        .update();
    String key = MediaKeys.source(asset);
    String uploadId = storage.createMultipartUpload(key, "video/mp4");
    UUID session = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO upload_session (id, asset_id, provider_upload_id, object_key, size_bytes,
                                        part_size_bytes, part_count, status, expires_at, created_at, version)
            VALUES (:id, :asset, :upload, :key, 1000, 5242880, 1, 'OPEN', :expires, now(), 0)
            """)
        .param("id", session)
        .param("asset", asset)
        .param("upload", uploadId)
        .param("key", key)
        .param("expires", Timestamp.from(expiresAt))
        .update();
    return asset;
  }

  private boolean providerHoldsUploadFor(UUID asset) {
    return !storage.listMultipartUploads(MediaKeys.asset(asset)).isEmpty();
  }

  private String sessionStatus(UUID asset) {
    return jdbc.sql("SELECT status FROM upload_session WHERE asset_id = :a")
        .param("a", asset)
        .query(String.class)
        .single();
  }

  @Test
  void anExpiredSessionIsAbortedAtTheProviderAndRecorded() {
    UUID expired = openSession(clock.instant().minusSeconds(1));
    UUID live = openSession(clock.instant().plus(Duration.ofHours(3)));

    sweeper.expireSessions();

    assertThat(providerHoldsUploadFor(expired)).isFalse(); // ⭐ the bill stops
    assertThat(sessionStatus(expired)).isEqualTo("EXPIRED");
    assertThat(assets.status(expired)).isEqualTo("FAILED");
    assertThat(providerHoldsUploadFor(live)).isTrue(); // still uploading: untouched
    assertThat(sessionStatus(live)).isEqualTo("OPEN");
  }

  @Test
  void aProviderUploadWithNoSessionIsAbortedOnlyOnceItIsADayOld() {
    String orphanKey = MediaKeys.source(UUID.randomUUID());
    storage.createMultipartUpload(orphanKey, "video/mp4"); // the api crashed before its insert
    UUID live = openSession(clock.instant().plus(Duration.ofDays(3)));

    assertThat(sweeper.abortOrphans()).isZero(); // too young: maybe the insert is still coming

    clock.advance(Duration.ofHours(25));
    assertThat(sweeper.abortOrphans()).isEqualTo(1);

    assertThat(storage.listMultipartUploads("media/"))
        .extracting(PendingUpload::key)
        .containsExactly(MediaKeys.source(live)); // the session-backed upload survives its age
  }

  @Test
  void sweepingTwiceIsHarmless() {
    UUID expired = openSession(clock.instant().minusSeconds(1));

    sweeper.expireSessions();
    sweeper.expireSessions();

    assertThat(sessionStatus(expired)).isEqualTo("EXPIRED");
  }

  @Test
  void aSecondReplicaSkipsTheSweepWhileTheFirstHoldsTheLock() {
    sweeper.sweep(); // replica A, through the ShedLock proxy: takes the lock for ≥ 1 minute
    UUID expired = openSession(clock.instant().minusSeconds(1));

    sweeper.sweep(); // ⭐ replica B, within that minute: skipped, not run twice

    assertThat(sessionStatus(expired)).isEqualTo("OPEN");
    assertThat(
            jdbc.sql("SELECT count(*) FROM shedlock WHERE name = 'upload-sweeper'")
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }
}
