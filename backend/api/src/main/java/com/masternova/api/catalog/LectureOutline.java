package com.masternova.api.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * What another module may know about a lecture in order to TRACK it (enrollment, Phase 10): which
 * course it belongs to, its title, whether it is a video and how long it is.
 *
 * @param durationSeconds the probed length for an uploaded video (media is the truth), otherwise
 *     the instructor's estimate; 0 = unknown
 */
public record LectureOutline(
    UUID lectureId, UUID courseId, String title, boolean video, int durationSeconds) {

  public LectureOutline {
    Objects.requireNonNull(lectureId, "lectureId");
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(title, "title");
  }
}
