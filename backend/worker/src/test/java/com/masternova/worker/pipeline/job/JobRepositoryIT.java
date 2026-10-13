package com.masternova.worker.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.worker.MutableClock;
import com.masternova.worker.TestcontainersConfiguration;
import com.masternova.worker.pipeline.TestAssets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** The claim / lease protocol on real Postgres: SKIP LOCKED, lapsed leases, stale finishers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, JobRepositoryIT.Config.class})
class JobRepositoryIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static final Duration LEASE = Duration.ofMinutes(2);

  @Autowired JobRepository jobs;
  @Autowired MutableClock clock;
  @Autowired DataSource dataSource;
  TestAssets assets;

  @BeforeEach
  void setUp() {
    assets = new TestAssets(dataSource);
    assets.deleteAll();
    clock.reset();
  }

  @AfterEach
  void tearDown() {
    assets.deleteAll();
  }

  @Test
  void enqueueingTheSameUnitTwiceCreatesOneJob() {
    UUID asset = assets.uploadedAsset();

    assertThat(jobs.enqueue(JobSpec.of(asset, JobType.PROBE))).isTrue();
    assertThat(jobs.enqueue(JobSpec.of(asset, JobType.PROBE))).isFalse(); // ⭐ redelivered event
    assertThat(jobs.jobsOf(asset)).hasSize(1);
  }

  @Test
  void concurrentWorkersNeverClaimTheSameJob() throws Exception {
    UUID asset = assets.uploadedAsset();
    for (String rung : List.of("240p", "360p", "480p", "720p", "1080p", "1440p")) {
      jobs.enqueue(new JobSpec(asset, JobType.TRANSCODE, rung));
    }
    List<UUID> claimed = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      List<Future<?>> workers = new ArrayList<>();
      for (int w = 0; w < 4; w++) {
        String id = "worker-" + w;
        workers.add(
            pool.submit(
                () -> {
                  start.await();
                  jobs.claim(2, LEASE, id).forEach(j -> claimed.add(j.id()));
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : workers) {
        f.get();
      }
    }
    assertThat(claimed).hasSize(6).doesNotHaveDuplicates();
  }

  @Test
  void aDeadWorkersJobIsClaimableOnceItsLeaseLapses() {
    UUID asset = assets.uploadedAsset();
    jobs.enqueue(JobSpec.of(asset, JobType.PROBE));
    Job first = jobs.claim(1, LEASE, "worker-a").getFirst();

    assertThat(jobs.claim(1, LEASE, "worker-b")).isEmpty(); // leased
    clock.advance(LEASE.plusSeconds(1)); // worker-a was SIGKILLed: no heartbeat
    Job again = jobs.claim(1, LEASE, "worker-b").getFirst();

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(again.attempts()).isEqualTo(2);
    // ⭐ the dead worker's late finish changes nothing — the job is worker-b's now
    assertThat(jobs.complete(first.id(), "worker-a")).isFalse();
    assertThat(jobs.heartbeat(first.id(), "worker-a", LEASE, 50)).isFalse();
    assertThat(jobs.complete(again.id(), "worker-b")).isTrue();
  }

  @Test
  void aJobThatLostItsWorkerOnTheLastAttemptIsBuriedInsteadOfReclaimed() {
    UUID asset = assets.uploadedAsset();
    jobs.enqueue(JobSpec.of(asset, JobType.TRANSCODE));
    for (int attempt = 1; attempt <= 3; attempt++) {
      assertThat(jobs.buryAbandoned(3)).isEmpty();
      jobs.claim(1, LEASE, "worker-" + attempt); // …and every worker is OOM-killed
      clock.advance(LEASE.plusSeconds(1));
    }

    assertThat(jobs.buryAbandoned(3)).containsExactly(asset);
    assertThat(jobs.claim(1, LEASE, "worker-4")).isEmpty(); // ⭐ no fourth worker taken down
    assertThat(jobs.jobsOf(asset))
        .singleElement()
        .satisfies(j -> assertThat(j.status()).isEqualTo("DEAD"));
  }

  @Test
  void aReleasedJobIsDueAtOnceWithoutCountingTheAttempt() {
    UUID asset = assets.uploadedAsset();
    jobs.enqueue(JobSpec.of(asset, JobType.PROBE));
    Job job = jobs.claim(1, LEASE, "worker-a").getFirst();

    assertThat(jobs.release(job.id(), "worker-a")).isTrue();

    assertThat(jobs.claim(1, LEASE, "worker-b"))
        .singleElement()
        .satisfies(j -> assertThat(j.attempts()).isEqualTo(1));
  }

  @Test
  void aHeartbeatKeepsTheLease() {
    UUID asset = assets.uploadedAsset();
    jobs.enqueue(JobSpec.of(asset, JobType.PROBE));
    Job job = jobs.claim(1, LEASE, "worker-a").getFirst();

    clock.advance(Duration.ofMinutes(1));
    assertThat(jobs.heartbeat(job.id(), "worker-a", LEASE, 40)).isTrue();
    clock.advance(
        Duration.ofMinutes(1).plusSeconds(30)); // past the FIRST lease, inside the new one

    assertThat(jobs.claim(1, LEASE, "worker-b")).isEmpty();
  }

  @Test
  void aRescheduledJobWaitsForItsBackoff() {
    UUID asset = assets.uploadedAsset();
    jobs.enqueue(JobSpec.of(asset, JobType.PROBE));
    Job job = jobs.claim(1, LEASE, "worker-a").getFirst();

    jobs.reschedule(job.id(), "worker-a", clock.instant().plusSeconds(30), "storage down");

    assertThat(jobs.claim(1, LEASE, "worker-a")).isEmpty();
    clock.advance(Duration.ofSeconds(31));
    assertThat(jobs.claim(1, LEASE, "worker-a")).hasSize(1);
  }
}
