package com.masternova.api.enrollment.web;

import com.masternova.api.enrollment.application.LearningQueries;
import com.masternova.api.enrollment.web.dto.CourseProgressResponse;
import com.masternova.api.enrollment.web.dto.LearningCourseResponse;
import com.masternova.api.enrollment.web.dto.LectureProgressResponse;
import com.masternova.api.identity.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The learner's reads: "My Learning", a course's progress, a lecture's resume point. */
@RestController
class LearningController {

  private final LearningQueries learning;

  LearningController(LearningQueries learning) {
    this.learning = learning;
  }

  /** Not paged: a learner's own courses, one row each (API conventions §15). */
  @GetMapping("/api/v1/me/learning")
  List<LearningCourseResponse> dashboard(CurrentUser me) {
    return learning.dashboard(me.id()).stream().map(LearningCourseResponse::from).toList();
  }

  @GetMapping("/api/v1/me/courses/{courseId}/progress")
  CourseProgressResponse course(CurrentUser me, @PathVariable UUID courseId) {
    return CourseProgressResponse.from(learning.course(me.id(), courseId));
  }

  @GetMapping("/api/v1/me/progress/{lectureId}")
  LectureProgressResponse lecture(CurrentUser me, @PathVariable UUID lectureId) {
    return LectureProgressResponse.from(learning.lecture(me.id(), lectureId));
  }
}
