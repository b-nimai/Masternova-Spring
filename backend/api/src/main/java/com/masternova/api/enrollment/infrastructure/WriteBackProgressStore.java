package com.masternova.api.enrollment.infrastructure;

import com.masternova.api.enrollment.domain.ProgressStore;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Progress through a Redis write-back buffer, flushed to the store it wraps (ADR-0022).
 *
 * <p>⭐ DECORATOR: same interface as {@code ProgressWriter}, wraps it, adds write-back. Force: ~67
 * heartbeats/s per 1,000 viewers that Postgres doesn't need to see one by one. Callers can't tell;
 * removing the buffer is removing one wrapper in {@code EnrollmentConfig}.
 *
 * <ul>
 *   <li>{@link #record}: one Lua call merges the heartbeat into {@value #DIRTY} — field {@code
 *       user:lecture}, value {@code last|max|atMillis}. Atomic in Redis, so two replicas can't race
 *       the read-modify-write of {@code max}. No SQL.
 *   <li>{@link #flush}: {@code RENAME} {@value #DIRTY} → {@value #FLUSHING} (atomic: the next
 *       heartbeat starts a new hash), write the batch through the wrapped store, delete what was
 *       written. A {@value #FLUSHING} left by a crashed flush is written first — again, harmlessly:
 *       the merge is idempotent.
 *   <li>{@link #find}: the wrapped store's rows, merged with what is still in either hash, so a
 *       resume never goes back in time.
 *   <li>⭐ Fails open: Redis down → the heartbeat is written through (counted as {@code direct}),
 *       reads fall back to the rows. Never a 500 for a bookmark.
 * </ul>
 */
@DesignPattern(
    value = Pattern.DECORATOR,
    role = "Decorator: write-back buffer in Redis, flushed every 30 s",
    note = "patterns/docs/13-decorator.md")
public final class WriteBackProgressStore implements ProgressStore {

  static final String DIRTY = "progress:v1:dirty";
  static final String FLUSHING = "progress:v1:flushing";

  /** A flush writes through in transactions of at most this many rows. */
  static final int BATCH = 1_000;

  /**
   * ⭐ {@link ProgressUpdate#merge} in Lua — the same rule, the same tie-break. {@code %.0f}: Lua
   * 5.1's numbers are doubles, and a plain {@code ..} would print a 13-digit epoch as {@code
   * 1.7e+12}.
   */
  static final RedisScript<Long> MERGE =
      RedisScript.of(
          """
          local last, max, at = tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4])
          local cur = redis.call('HGET', KEYS[1], ARGV[1])
          if cur then
            local l, m, a = string.match(cur, '^(%d+)|(%d+)|(%d+)$')
            l, m, a = tonumber(l), tonumber(m), tonumber(a)
            if m > max then max = m end
            if a > at or (a == at and l > last) then last, at = l, a end
          end
          redis.call('HSET', KEYS[1], ARGV[1], string.format('%.0f|%.0f|%.0f', last, max, at))
          return 1
          """,
          Long.class);

  /**
   * Moves the dirty hash aside for flushing — only if no earlier batch is still waiting there
   * ({@code RENAME} would overwrite it; −1). 0 = nothing to flush.
   */
  static final RedisScript<Long> SWAP =
      RedisScript.of(
          """
          if redis.call('EXISTS', KEYS[2]) == 1 then return -1 end
          if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
          redis.call('RENAME', KEYS[1], KEYS[2])
          return 1
          """,
          Long.class);

  /**
   * ⭐ Deletes from the flushing hash only the entries this flush read AND wrote — still holding the
   * same value. If a slow flush overlapped another (the lock expired), the other's new batch is
   * left alone; when every entry is gone, Redis drops the key itself. ARGV: field, value, …
   */
  static final RedisScript<Long> RELEASE =
      RedisScript.of(
          """
          local n = 0
          for i = 1, #ARGV, 2 do
            if redis.call('HGET', KEYS[1], ARGV[i]) == ARGV[i + 1] then
              n = n + redis.call('HDEL', KEYS[1], ARGV[i])
            end
          end
          return n
          """,
          Long.class);

  private static final Logger log = LoggerFactory.getLogger(WriteBackProgressStore.class);

  private final ProgressStore inner;
  private final StringRedisTemplate redis;
  private final Counter buffered;
  private final Counter direct;
  private final Counter flushed;

  public WriteBackProgressStore(
      ProgressStore inner, StringRedisTemplate redis, MeterRegistry meters) {
    this.inner = inner;
    this.redis = redis;
    this.buffered = meters.counter("enrollment.progress.heartbeats", "path", "buffered");
    this.direct = meters.counter("enrollment.progress.heartbeats", "path", "direct");
    this.flushed = meters.counter("enrollment.progress.flushed");
  }

  @Override
  public void record(ProgressUpdate update) {
    try {
      redis.execute(
          MERGE,
          List.of(DIRTY),
          field(update.userId(), update.lectureId()),
          Integer.toString(update.lastPositionSeconds()),
          Integer.toString(update.maxPositionSeconds()),
          Long.toString(update.at().toEpochMilli()));
      buffered.increment();
    } catch (DataAccessException e) { // ⭐ Redis down: degrade to write-through, never fail
      log.warn("progress buffer unavailable, writing through: {}", e.getMessage());
      direct.increment();
      inner.record(update);
    }
  }

  @Override
  public void recordAll(Collection<ProgressUpdate> updates) {
    ProgressUpdate.mergeByRow(updates).forEach(this::record);
  }

  /**
   * The buffer, then the rows. ⭐ Read in the order data MOVES — dirty → flushing → Postgres — so an
   * update that moves on between two reads is caught by the next one: a swap after the dirty read
   * lands in flushing, a flush that commits after the flushing read is in the rows.
   */
  @Override
  public Map<UUID, ProgressUpdate> find(UUID userId, Collection<UUID> lectureIds) {
    Map<UUID, ProgressUpdate> merged = new HashMap<>();
    if (lectureIds.isEmpty()) {
      return merged;
    }
    List<UUID> ids = List.copyOf(lectureIds);
    List<Object> fields = ids.stream().<Object>map(id -> field(userId, id)).toList();
    try {
      for (String hash : List.of(DIRTY, FLUSHING)) {
        List<Object> values = redis.opsForHash().multiGet(hash, fields);
        for (int i = 0; i < ids.size(); i++) {
          parse(userId, ids.get(i), values.get(i))
              .ifPresent(u -> merged.merge(u.lectureId(), u, ProgressUpdate::merge));
        }
      }
    } catch (DataAccessException e) { // the rows are at most one flush behind
      log.warn("progress buffer unavailable, reading rows only: {}", e.getMessage());
    }
    inner
        .find(userId, lectureIds)
        .forEach((id, row) -> merged.merge(id, row, ProgressUpdate::merge));
    return merged;
  }

  /**
   * Writes the buffer to the wrapped store. Called by {@code ProgressFlushJob} on one replica.
   * Throws when the wrapped store fails: the batch stays in {@value #FLUSHING} for the next try.
   *
   * @return how many rows were written
   */
  public int flush() {
    int written = drain(); // ⭐ a batch a crashed flush left behind goes first
    Long swapped = redis.execute(SWAP, List.of(DIRTY, FLUSHING));
    if (swapped != null && swapped == 1) {
      written += drain();
    }
    flushed.increment(written);
    return written;
  }

  private int drain() {
    Map<Object, Object> entries = redis.opsForHash().entries(FLUSHING);
    if (entries.isEmpty()) {
      return 0;
    }
    List<ProgressUpdate> batch = new ArrayList<>(entries.size());
    entries.forEach((field, value) -> parse(field, value).ifPresent(batch::add));
    for (int from = 0; from < batch.size(); from += BATCH) {
      inner.recordAll(batch.subList(from, Math.min(from + BATCH, batch.size())));
    }
    List<Object> written = new ArrayList<>(entries.size() * 2); // only after every chunk committed
    entries.forEach(
        (field, value) -> {
          written.add(String.valueOf(field));
          written.add(String.valueOf(value));
        });
    redis.execute(RELEASE, List.of(FLUSHING), written.toArray());
    return batch.size();
  }

  static String field(UUID userId, UUID lectureId) {
    return userId + ":" + lectureId;
  }

  static String value(ProgressUpdate u) {
    return u.lastPositionSeconds() + "|" + u.maxPositionSeconds() + "|" + u.at().toEpochMilli();
  }

  private static Optional<ProgressUpdate> parse(Object field, Object value) {
    String[] key = String.valueOf(field).split(":");
    if (key.length != 2) {
      return Optional.empty();
    }
    try {
      return parse(UUID.fromString(key[0]), UUID.fromString(key[1]), value);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** An unreadable entry is dropped, never a failed flush: one bad value can't block the rest. */
  static Optional<ProgressUpdate> parse(UUID userId, UUID lectureId, Object value) {
    if (value == null) {
      return Optional.empty();
    }
    String[] parts = String.valueOf(value).split("\\|");
    try {
      return Optional.of(
          new ProgressUpdate(
              userId,
              lectureId,
              Integer.parseInt(parts[0]),
              Integer.parseInt(parts[1]),
              Instant.ofEpochMilli(Long.parseLong(parts[2]))));
    } catch (RuntimeException e) { // wrong arity, not a number, out of range
      log.warn("dropping an unreadable progress entry {}: {}", value, e.getMessage());
      return Optional.empty();
    }
  }
}
