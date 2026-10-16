package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.application.LearningQueries.LectureResume;
import java.util.UUID;

/** Where the player starts a lecture ({@code positionSeconds}), and how far the learner got. */
public record LectureProgressResponse(
    UUID lectureId, int positionSeconds, int maxPositionSeconds, boolean completed) {

  public static LectureProgressResponse from(LectureResume r) {
    return new LectureProgressResponse(
        r.lectureId(), r.positionSeconds(), r.maxPositionSeconds(), r.completed());
  }
}
