package com.masternova.worker.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import com.masternova.storage.SeaweedFsContainer;
import com.masternova.worker.TestcontainersConfiguration;
import com.masternova.worker.pipeline.job.JobRepository;
import com.masternova.worker.pipeline.job.JobSpec;
import com.masternova.worker.pipeline.job.JobType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * ⭐ THE Phase 7 proof (video-pipeline LLD §5, "crash mid-transcode"): the REAL worker jar runs as a
 * child JVM; it is SIGKILLed after some of a rung's segments are in storage but before that rung's
 * playlist (its commit marker) is; a second worker process finishes the job. The result must be
 * indistinguishable from an uninterrupted run: the same object keys, one rendition row per rung,
 * every job DONE — no duplicates, no orphans.
 *
 * <p>This test process only provides the infrastructure (Postgres + Flyway, SeaweedFS, Redis) and
 * watches; its own poller is off (test config).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class PipelineCrashIT {

  static final Duration LEASE =
      Duration.ofSeconds(4); // short: the rerun starts soon after the kill

  @Autowired JobRepository jobs;
  @Autowired PipelineStore store;
  @Autowired ObjectStorage storage;
  @Autowired DataSource dataSource;
  @Autowired JdbcConnectionDetails database; // the containers' real addresses (@ServiceConnection)
  @Autowired DataRedisConnectionDetails redis;
  @Autowired SeaweedFsContainer seaweed;
  @TempDir Path tmp;
  TestAssets assets;
  final List<Process> workers = new ArrayList<>();

  @BeforeEach
  void setUp() {
    assets = new TestAssets(dataSource);
    assets.deleteAll();
  }

  @AfterEach
  void tearDown() {
    workers.forEach(PipelineCrashIT::kill);
    assets.deleteAll();
  }

  @Test
  void aWorkerKilledMidUploadLeavesNoDuplicatesAndTheRerunMatchesACleanRun() throws Exception {
    Path clip = TestClips.mp4(tmp, 120, 640, 360, true); // 20 segments per rung: a wide window

    // the control: an uninterrupted run of the same clip
    UUID control = uploadedAndQueued(clip);
    Process clean = startWorker("clean");
    awaitReady(control, Duration.ofMinutes(3));
    kill(clean);

    // the crash: kill the worker once the first segments of a rung are in storage, before its
    // playlist is — the moment a naive pipeline would leave a half-written rung behind
    UUID crashed = uploadedAndQueued(clip);
    Process victim = startWorker("victim");
    String rungPrefix = awaitPartialRung(crashed, Duration.ofMinutes(2));
    kill(victim); // ⭐ SIGKILL: no finally, no shutdown hook, no heartbeat
    List<String> atTheKill = storage.listKeys(rungPrefix);
    assertThat(atTheKill)
        .as("killed mid-upload")
        .isNotEmpty()
        .noneMatch(k -> k.endsWith("index.m3u8"));
    assertThat(storage.listKeys(MediaKeys.hls(crashed)))
        .as("no master playlist while a rung is incomplete")
        .noneMatch(k -> k.equals(MediaKeys.master(crashed)));

    startWorker("survivor"); // its poll finds the RUNNING job once the dead worker's lease lapses
    awaitReady(crashed, Duration.ofMinutes(3));

    // ⭐ identical to the clean run: same keys (relative to the asset), same renditions
    assertThat(relativeKeys(crashed)).isEqualTo(relativeKeys(control));
    assertThat(store.renditions(crashed))
        .extracting(r -> r.rung() + ":" + r.segmentCount())
        .isEqualTo(
            store.renditions(control).stream()
                .map(r -> r.rung() + ":" + r.segmentCount())
                .toList());
    assertThat(jobs.jobsOf(crashed)).allMatch(JobRepository.JobState::done);
    // the killed job ran twice; nothing else did
    assertThat(
            JdbcClient.create(dataSource)
                .sql("SELECT max(attempts) FROM pipeline_job WHERE asset_id = :a")
                .param("a", crashed)
                .query(Integer.class)
                .single())
        .isEqualTo(2);
  }

  // ------------------------------------------------------------------ the child worker

  private Process startWorker(String name) throws IOException {
    Path jar = workerJar();
    Path work = Files.createDirectories(tmp.resolve("work-" + name));
    List<String> command =
        new ArrayList<>(
            List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-Xmx384m",
                "-jar",
                jar.toString()));
    Map<String, String> props =
        Map.ofEntries(
            Map.entry("spring.datasource.url", database.getJdbcUrl()),
            Map.entry("spring.datasource.username", database.getUsername()),
            Map.entry("spring.datasource.password", database.getPassword()),
            Map.entry("spring.data.redis.host", redis.getStandalone().getHost()),
            Map.entry("spring.data.redis.port", Integer.toString(redis.getStandalone().getPort())),
            Map.entry("masternova.storage.endpoint", seaweed.endpoint().toString()),
            Map.entry("masternova.storage.access-key", SeaweedFsContainer.ACCESS_KEY),
            Map.entry("masternova.storage.secret-key", SeaweedFsContainer.SECRET_KEY),
            Map.entry("masternova.outbox.relay-enabled", "false"), // only the pipeline under test
            Map.entry("masternova.pipeline.enabled", "true"),
            Map.entry("masternova.pipeline.ladder", "fast"),
            Map.entry("masternova.pipeline.work-dir", work.toString()),
            Map.entry("masternova.pipeline.slots", "1"),
            Map.entry("masternova.pipeline.poll-interval", "200ms"),
            Map.entry("masternova.pipeline.lease", LEASE.toSeconds() + "s"),
            Map.entry("masternova.pipeline.heartbeat", "1s"),
            Map.entry("masternova.pipeline.sweep-cron", "-"), // the sweeper: off
            Map.entry("server.port", "0"),
            Map.entry("management.health.mail.enabled", "false"),
            Map.entry("spring.main.banner-mode", "off"));
    props.forEach((k, v) -> command.add("--" + k + "=" + v));
    Process process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(tmp.resolve("worker-" + name + ".log").toFile())
            .start();
    workers.add(process);
    return process;
  }

  /** The repackaged Spring Boot jar from this module's {@code package} phase. */
  private static Path workerJar() throws IOException {
    try (Stream<Path> files = Files.list(Path.of("target"))) {
      return files
          .filter(p -> p.getFileName().toString().matches("worker-.*\\.jar"))
          .filter(p -> !p.getFileName().toString().contains("-tests"))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "run `mvn verify`: the worker jar is built in package"));
    }
  }

  /** SIGKILL the JVM and its ffmpeg children — what a node's OOM killer or a pod eviction does. */
  private static void kill(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    try {
      process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ------------------------------------------------------------------ helpers

  private UUID uploadedAndQueued(Path clip) {
    UUID asset = assets.uploadedAsset();
    storage.put(MediaKeys.source(asset), clip, "video/mp4");
    jobs.enqueue(JobSpec.of(asset, JobType.PROBE)); // what MediaUploadedHandler does
    return asset;
  }

  private void awaitReady(UUID asset, Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (!"READY".equals(assets.status(asset))) {
      assertThat(Instant.now())
          .as("asset %s READY in time (status %s)", asset, assets.status(asset))
          .isBefore(deadline);
      Thread.sleep(100);
    }
  }

  /** Waits until some rung has segments in storage but no playlist yet; returns its prefix. */
  private String awaitPartialRung(UUID asset, Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      for (String rung : List.of("240p", "360p")) {
        String prefix = MediaKeys.rung(asset, rung);
        List<String> keys = storage.listKeys(prefix);
        if (keys.stream().anyMatch(k -> k.endsWith(".m4s"))
            && keys.stream().noneMatch(k -> k.endsWith("index.m3u8"))) {
          return prefix;
        }
      }
      Thread.sleep(5);
    }
    throw new AssertionError("no rung was ever caught half-uploaded");
  }

  private List<String> relativeKeys(UUID asset) {
    String prefix = MediaKeys.asset(asset);
    return storage.listKeys(prefix).stream()
        .map(k -> k.substring(prefix.length()))
        .sorted()
        .toList();
  }
}
