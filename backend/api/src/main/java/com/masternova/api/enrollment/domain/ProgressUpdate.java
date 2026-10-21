package com.masternova.api.enrollment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Where one learner is in one lecture, as far as some heartbeats say — the unit of the write-back
 * buffer (ADR-0022). A heartbeat is the update with {@code last = max = position}.
 *
 * <p>⭐ {@link #merge} is a SEMILATTICE join: commutative, associative and idempotent. {@code max}
 * only goes up; {@code last} is the newest one (by heartbeat time, ties to the larger position). So
 * heartbeats from two tabs, a beacon racing the last heartbeat, and a flush replayed after a crash
 * all end in the same row, whatever the order and however many times — {@code ProgressUpdateTest}
 * checks the algebra. The Lua script and the SQL upsert implement the same rule.
 *
 * @param at the heartbeat's time, in whole MILLISECONDS — what Redis stores, so the three
 *     implementations compare equal values
 */
public record ProgressUpdate(
    UUID userId, UUID lectureId, int lastPositionSeconds, int maxPositionSeconds, Instant at) {

  /** 24 h: no lecture is longer; anything above is a broken or hostile client. */
  public static final int MAX_POSITION_SECONDS = 86_400;

  /** Which row an update is about. */
  public record Key(UUID userId, UUID lectureId) {}

  public ProgressUpdate {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(lectureId, "lectureId");
    Objects.requireNonNull(at, "at");
    if (lastPositionSeconds < 0 || maxPositionSeconds > MAX_POSITION_SECONDS) {
      throw new IllegalArgumentException("positions must lie in 0–" + MAX_POSITION_SECONDS + " s");
    }
    if (maxPositionSeconds < lastPositionSeconds) {
      throw new IllegalArgumentException("max must be ≥ last"); // the merge's invariant
    }
    at = at.truncatedTo(ChronoUnit.MILLIS);
  }

  public static ProgressUpdate heartbeat(
      UUID userId, UUID lectureId, int positionSeconds, Instant at) {
    return new ProgressUpdate(userId, lectureId, positionSeconds, positionSeconds, at);
  }

  public Key key() {
    return new Key(userId, lectureId);
  }

  /** The join of two updates of the same row. */
  public ProgressUpdate merge(ProgressUpdate other) {
    if (!key().equals(other.key())) {
      throw new IllegalArgumentException("can't merge progress of different rows");
    }
    ProgressUpdate newer = isNewerThan(other) ? this : other;
    return new ProgressUpdate(
        userId,
        lectureId,
        newer.lastPositionSeconds,
        Math.max(maxPositionSeconds, other.maxPositionSeconds),
        newer.at);
  }

  /** One update per row: what a batch must be before one SQL upsert may touch each row once. */
  public static List<ProgressUpdate> mergeByRow(Collection<ProgressUpdate> updates) {
    Map<Key, ProgressUpdate> rows = new LinkedHashMap<>();
    updates.forEach(u -> rows.merge(u.key(), u, ProgressUpdate::merge));
    return List.copyOf(rows.values());
  }

  private boolean isNewerThan(ProgressUpdate other) {
    int byTime = at.compareTo(other.at);
    return byTime != 0 ? byTime > 0 : lastPositionSeconds >= other.lastPositionSeconds;
  }
}
