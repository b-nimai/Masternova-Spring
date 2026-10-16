package com.masternova.api.enrollment.domain;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Where learners are in their lectures. Two implementations: {@code ProgressWriter} writes through
 * to Postgres (validated, one transaction per batch), {@code WriteBackProgressStore} buffers in
 * Redis and flushes to it every 30 s (ADR-0022). Callers can't tell which they hold.
 */
public interface ProgressStore {

  /** One heartbeat (or beacon). Never throws for an unknown lecture: that is checked later. */
  void record(ProgressUpdate update);

  /** Many updates, merged by row first. */
  void recordAll(Collection<ProgressUpdate> updates);

  /** The latest known position per lecture; lectures with no progress are absent. */
  Map<UUID, ProgressUpdate> find(UUID userId, Collection<UUID> lectureIds);
}
