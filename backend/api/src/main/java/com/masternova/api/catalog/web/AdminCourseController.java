package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseLifecycleService;
import com.masternova.api.catalog.web.dto.CourseDetailResponse;
import com.masternova.api.identity.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The reviewer's side of the lifecycle. ADMIN only (enforced on the service). */
@RestController
@RequestMapping("/api/v1/admin/courses/{id}")
class AdminCourseController {

  private final CourseLifecycleService lifecycle;

  AdminCourseController(CourseLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @PostMapping("/publish")
  CourseDetailResponse publish(@PathVariable UUID id, CurrentUser me) {
    return CourseDetailResponse.from(lifecycle.publish(id, Viewers.of(me)));
  }
}
