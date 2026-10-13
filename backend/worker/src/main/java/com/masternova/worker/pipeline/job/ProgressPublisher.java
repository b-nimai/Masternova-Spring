package com.masternova.worker.pipeline.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Live progress out of the worker (video-pipeline LLD §5): Redis pub/sub on {@code
 * media:asset:{id}}, which every api replica's SSE streams subscribe to.
 *
 * <ul>
 *   <li>{@code progress}: a running job's percentage, at most once a second per job. ⭐ LOSSY by
 *       design — a missed tick is replaced by the next one; nothing waits for delivery;
 *   <li>{@code changed}: a job finished or died, so the asset may have changed state — the api
 *       re-reads it from the database, which stays the source of truth.
 * </ul>
 *
 * A Redis outage never fails a job: publishing errors are logged and dropped.
 */
@Component
public class ProgressPublisher {

  private static final Logger log = LoggerFactory.getLogger(ProgressPublisher.class);
  static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final Clock clock;
  private final Map<UUID, Instant> lastSent = new ConcurrentHashMap<>();

  ProgressPublisher(StringRedisTemplate redis, JsonMapper json, Clock clock) {
    this.redis = redis;
    this.json = json;
    this.clock = clock;
  }

  public static String channel(UUID assetId) {
    return "media:asset:" + assetId;
  }

  public void progress(Job job, int percent) {
    Instant now = clock.instant();
    Instant last = lastSent.get(job.id());
    if (percent < 100 && last != null && now.isBefore(last.plus(MIN_INTERVAL))) {
      return; // throttled
    }
    lastSent.put(job.id(), now);
    publish(
        job.assetId(),
        Map.of(
            "kind", "progress",
            "assetId", job.assetId().toString(),
            "job", job.type().name(),
            "rung", job.rung(),
            "percent", percent));
  }

  /** After the job's transaction committed (DONE, DEAD or rescheduled). */
  public void changed(Job job) {
    lastSent.remove(job.id());
    publish(job.assetId(), Map.of("kind", "changed", "assetId", job.assetId().toString()));
  }

  private void publish(UUID assetId, Map<String, Object> message) {
    try {
      redis.convertAndSend(channel(assetId), json.writeValueAsString(message));
    } catch (RuntimeException e) {
      log.debug("progress for {} not published: {}", assetId, e.getMessage());
    }
  }
}
