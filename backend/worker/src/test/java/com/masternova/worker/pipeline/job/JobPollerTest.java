package com.masternova.worker.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masternova.worker.pipeline.TestPipelineProperties;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class JobPollerTest {

  final JobRepository jobs = mock(JobRepository.class);
  final JobRunner runner = mock(JobRunner.class);

  @Test
  void claimsOnlyAsManyJobsAsThereAreFreeSlots() throws Exception {
    Job job = new Job(UUID.randomUUID(), UUID.randomUUID(), JobType.PROBE, "", 1);
    CountDownLatch release = new CountDownLatch(1);
    when(jobs.claim(anyInt(), any(), anyString())).thenReturn(List.of(job));
    org.mockito.Mockito.doAnswer(
            inv -> {
              release.await();
              return null;
            })
        .when(runner)
        .execute(any(), anyString());

    try (JobPoller poller =
        new JobPoller(jobs, runner, TestPipelineProperties.defaults())) { // 1 slot
      poller.poll(); // takes the slot
      verify(runner, timeout(2000)).execute(any(), anyString());
      poller.poll(); // ⭐ slot busy: doesn't even ask for more work
      verify(jobs, org.mockito.Mockito.times(1)).claim(anyInt(), any(), anyString());
      release.countDown();
    }
  }

  @Test
  void nothingDueRunsNothing() {
    when(jobs.claim(anyInt(), any(), anyString())).thenReturn(List.of());
    try (JobPoller poller = new JobPoller(jobs, runner, TestPipelineProperties.defaults())) {
      poller.poll();
    }
    verify(runner, never()).execute(any(), any());
  }

  @Test
  void everyPollFirstBuriesJobsWhoseWorkerDiedOnTheirLastAttempt() {
    when(jobs.claim(anyInt(), any(), anyString())).thenReturn(List.of());
    try (JobPoller poller = new JobPoller(jobs, runner, TestPipelineProperties.defaults())) {
      poller.poll();
    }
    verify(runner).buryAbandoned();
  }

  @Test
  void closingTellsTheRunnerFirstSoInterruptedJobsAreHandedBack() {
    new JobPoller(jobs, runner, TestPipelineProperties.defaults()).close();
    verify(runner).shutDown();
  }

  @Test
  void theWorkerIdNamesHostAndProcess() {
    assertThat(JobPoller.workerId()).matches("[^:]+:\\d+:[0-9a-f]{8}");
  }
}
