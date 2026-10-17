package com.masternova.api.enrollment.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * An update that passed the flush's checks (the lecture exists, the learner is enrolled), with what
 * catalog said about the lecture: the row's course and the duration completion is measured by.
 */
public record TrackedProgress(ProgressUpdate update, UUID courseId, int durationSeconds) {

  public TrackedProgress {
    Objects.requireNonNull(update, "update");
    Objects.requireNonNull(courseId, "courseId");
  }
}
