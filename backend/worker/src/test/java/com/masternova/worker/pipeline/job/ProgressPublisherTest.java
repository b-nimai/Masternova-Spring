package com.masternova.worker.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.masternova.worker.MutableClock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class ProgressPublisherTest {

  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  final MutableClock clock = new MutableClock();
  final ProgressPublisher publisher = new ProgressPublisher(redis, new JsonMapper(), clock);
  final Job job = new Job(UUID.randomUUID(), UUID.randomUUID(), JobType.TRANSCODE, "720p", 1);

  @Test
  void ticksAreThrottledToOneASecondPerJobButTheEndAlwaysGoesOut() {
    publisher.progress(job, 10);
    publisher.progress(job, 11); // same second: dropped
    clock.advance(Duration.ofMillis(1001));
    publisher.progress(job, 30);
    publisher.progress(job, 100); // ⭐ never throttled

    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(redis, times(3)).convertAndSend(anyString(), message.capture());
    assertThat(message.getAllValues()).allMatch(m -> m.contains("\"kind\":\"progress\""));
    assertThat(message.getAllValues().getLast()).contains("\"percent\":100", "\"rung\":\"720p\"");
  }

  @Test
  void aChangeIsPublishedOnTheAssetsChannel() {
    publisher.changed(job);
    verify(redis)
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq(
                com.masternova.storage.MediaKeys.progressChannel(job.assetId())),
            anyString());
  }

  @Test
  void redisBeingDownNeverFailsAJob() {
    doThrow(new RedisConnectionFailureException("down"))
        .when(redis)
        .convertAndSend(anyString(), anyString());
    assertThatCode(() -> publisher.progress(job, 5)).doesNotThrowAnyException();
    assertThatCode(() -> publisher.changed(job)).doesNotThrowAnyException();
  }
}
