package com.masternova.worker.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.messaging.OutboxMessage;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.worker.TestcontainersConfiguration;
import com.masternova.worker.pipeline.job.Job;
import com.masternova.worker.pipeline.job.JobRepository;
import com.masternova.worker.pipeline.job.JobRepository.JobState;
import com.masternova.worker.pipeline.job.JobRunner;
import com.masternova.worker.pipeline.job.JobType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * ⭐ The whole DAG for real: an uploaded source in SeaweedFS, the {@code MediaUploaded} handler, the
 * real job runner, real ffmpeg (fast ladder), Postgres. The poller is off; {@link #drain()} claims
 * and runs jobs until none are due — the same calls the poller makes, deterministically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class PipelineIT {

  @Autowired MediaUploadedHandler handler;
  @Autowired JobRepository jobs;
  @Autowired JobRunner runner;
  @Autowired PipelineStore store;
  @Autowired ObjectStorage storage;
  @Autowired DataSource dataSource;
  @TempDir Path tmp;
  TestAssets assets;

  @BeforeEach
  void setUp() {
    assets = new TestAssets(dataSource);
    assets.deleteAll();
  }

  @AfterEach
  void tearDown() {
    assets.deleteAll();
  }

  private void uploaded(UUID asset, Path file) {
    storage.put(MediaKeys.source(asset), file, "video/mp4");
  }

  private void mediaUploaded(UUID asset) {
    handler.handle(
        new OutboxMessage(
            UUID.randomUUID(),
            "media.uploaded.v1",
            asset.toString(),
            "{\"assetId\": \"" + asset + "\", \"sourceKey\": \"" + MediaKeys.source(asset) + "\"}",
            Instant.now(),
            1));
  }

  /** Claim + run until nothing is due: the poller's loop, one job at a time. */
  private int drain() {
    int ran = 0;
    for (List<Job> due; !(due = jobs.claim(4, Duration.ofMinutes(2), "it")).isEmpty(); ) {
      for (Job job : due) {
        runner.execute(job, "it");
        ran++;
      }
    }
    return ran;
  }

  @Test
  void anUploadedVideoBecomesAReadyHlsLadder() throws Exception {
    UUID asset = assets.uploadedAsset();
    uploaded(asset, TestClips.mp4(tmp, 13, 1280, 720, true));

    mediaUploaded(asset);
    mediaUploaded(asset); // ⭐ the outbox redelivers: still one pipeline

    assertThat(drain()).isEqualTo(5); // PROBE, 2 × TRANSCODE, THUMBNAILS, PACKAGE
    assertThat(assets.status(asset)).isEqualTo("READY");
    assertThat(jobs.jobsOf(asset))
        .extracting(JobState::type)
        .containsExactlyInAnyOrder(
            JobType.PROBE,
            JobType.TRANSCODE,
            JobType.TRANSCODE,
            JobType.THUMBNAILS,
            JobType.PACKAGE);
    assertThat(jobs.jobsOf(asset)).allMatch(JobState::done);

    // the renditions the fast ladder makes of a 720p source, with what the master needs
    assertThat(store.renditions(asset))
        .extracting(r -> r.rung() + " " + r.width() + "x" + r.height() + " " + r.segmentCount())
        .containsExactly("240p 426x240 3", "360p 640x360 3");

    String master = storage.getText(MediaKeys.master(asset));
    assertThat(master).contains("RESOLUTION=426x240", "240p/index.m3u8", "360p/index.m3u8");
    assertThat(storage.listKeys(MediaKeys.rung(asset, "360p")))
        .containsExactly(
            MediaKeys.rung(asset, "360p") + "index.m3u8",
            MediaKeys.rung(asset, "360p") + "init.mp4",
            MediaKeys.segment(asset, "360p", 0),
            MediaKeys.segment(asset, "360p", 1),
            MediaKeys.segment(asset, "360p", 2));
    assertThat(storage.head(MediaKeys.poster(asset))).isPresent();
    assertThat(storage.getText(MediaKeys.spriteVtt(asset))).startsWith("WEBVTT");
    assertThat(
            JdbcClient.create(dataSource)
                .sql("SELECT duration_seconds FROM media_asset WHERE id = :id")
                .param("id", asset)
                .query(Integer.class)
                .single())
        .isEqualTo(13);
  }

  @Test
  void aFileThatIsNotAVideoFailsTheAssetAtOnceWithoutRetries() throws Exception {
    UUID asset = assets.uploadedAsset();
    uploaded(asset, Files.writeString(tmp.resolve("notes.mp4"), "definitely not a video"));

    mediaUploaded(asset);
    drain();

    assertThat(assets.status(asset)).isEqualTo("FAILED");
    assertThat(jobs.jobsOf(asset))
        .singleElement()
        .satisfies(j -> assertThat(j.status()).isEqualTo("DEAD")); // permanent: 1 attempt, DEAD
    assertThat(
            JdbcClient.create(dataSource)
                .sql("SELECT failure_code FROM media_asset WHERE id = :id")
                .param("id", asset)
                .query(String.class)
                .single())
        .isEqualTo("MEDIA_UNREADABLE");
  }
}
