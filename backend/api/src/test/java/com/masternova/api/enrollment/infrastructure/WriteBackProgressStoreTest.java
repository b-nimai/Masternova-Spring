package com.masternova.api.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.masternova.api.enrollment.domain.ProgressStore;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * The Decorator failing open — Redis down never fails a heartbeat or a resume — and the buffer's
 * text format. No Spring, no Redis (the Lua itself runs in {@code WriteBackProgressStoreIT}).
 */
class WriteBackProgressStoreTest {

  static final UUID USER = UUID.randomUUID();
  static final UUID LECTURE = UUID.randomUUID();
  static final Instant T0 = Instant.parse("2026-10-16T10:00:00.123Z");

  /** The wrapped component: remembers what reached it. */
  static final class FakeWriter implements ProgressStore {
    final List<ProgressUpdate> written = new ArrayList<>();
    final Map<UUID, ProgressUpdate> rows = new HashMap<>();

    @Override
    public void record(ProgressUpdate update) {
      written.add(update);
    }

    @Override
    public void recordAll(Collection<ProgressUpdate> updates) {
      written.addAll(updates);
    }

    @Override
    public Map<UUID, ProgressUpdate> find(UUID userId, Collection<UUID> lectureIds) {
      return rows;
    }
  }

  final FakeWriter inner = new FakeWriter();
  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  final WriteBackProgressStore store = new WriteBackProgressStore(inner, redis, meters);

  @Test
  @SuppressWarnings("unchecked")
  void redisDownAHeartbeatIsWrittenThrough() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new RedisConnectionFailureException("down"));
    ProgressUpdate beat = ProgressUpdate.heartbeat(USER, LECTURE, 42, T0);

    store.record(beat);

    assertThat(inner.written).containsExactly(beat);
    assertThat(meters.counter("enrollment.progress.heartbeats", "path", "direct").count())
        .isEqualTo(1);
  }

  @Test
  void redisDownAResumeReadsTheRows() {
    when(redis.opsForHash()).thenThrow(new RedisConnectionFailureException("down"));
    ProgressUpdate row = ProgressUpdate.heartbeat(USER, LECTURE, 300, T0);
    inner.rows.put(LECTURE, row);

    assertThat(store.find(USER, List.of(LECTURE))).containsEntry(LECTURE, row);
  }

  @Test
  void theBufferFormatRoundTripsAndJunkIsDropped() {
    ProgressUpdate u = new ProgressUpdate(USER, LECTURE, 30, 600, T0);
    assertThat(WriteBackProgressStore.value(u)).isEqualTo("30|600|" + T0.toEpochMilli());
    assertThat(WriteBackProgressStore.parse(USER, LECTURE, WriteBackProgressStore.value(u)))
        .contains(u);

    assertThat(WriteBackProgressStore.parse(USER, LECTURE, "30|600")).isEmpty();
    assertThat(WriteBackProgressStore.parse(USER, LECTURE, "a|b|c")).isEmpty();
    assertThat(WriteBackProgressStore.parse(USER, LECTURE, "60|30|1")).isEmpty(); // max < last
    assertThat(WriteBackProgressStore.parse(USER, LECTURE, null)).isEmpty();
  }
}
