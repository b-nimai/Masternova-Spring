package com.masternova.api.enrollment.domain;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** The {@code lecture_progress} rows: the flushed truth behind the buffer. */
public interface LectureProgressRepository {

  /**
   * Merges a batch into its rows in ONE statement (each row at most once: merge by row first). ⭐
   * The SQL is {@link ProgressUpdate#merge}, so writing a batch twice changes nothing. {@code
   * completed_at} is set once, never unset.
   */
  void upsert(Collection<TrackedProgress> rows);

  Map<UUID, ProgressUpdate> find(UUID userId, Collection<UUID> lectureIds);
}
