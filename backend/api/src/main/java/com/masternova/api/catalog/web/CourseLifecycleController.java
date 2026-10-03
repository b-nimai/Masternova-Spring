package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseLifecycleService;
import com.masternova.api.catalog.domain.CourseAction;
import com.masternova.api.catalog.web.dto.CourseDetailResponse;
import com.masternova.api.catalog.web.dto.ReadinessResponse;
import com.masternova.api.identity.CurrentUser;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The author's lifecycle actions. Transitions carry no {@code expectedVersion} (API conventions
 * §3): they re-read the course and re-run their guards; {@code @Version} rejects a concurrent one.
 */
@RestController
@RequestMapping("/api/v1/instructor/courses/{id}")
class CourseLifecycleController {

  private final CourseLifecycleService lifecycle;

  CourseLifecycleController(CourseLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @GetMapping("/readiness")
  ReadinessResponse readiness(@PathVariable UUID id, CurrentUser me) {
    return ReadinessResponse.from(lifecycle.readiness(id, Viewers.of(me)));
  }

  /** ⭐ The path regex limits the verbs: {@code /publish} isn't an author route (it 404s here). */
  @PostMapping("/{action:submit|withdraw|unpublish|archive}")
  CourseDetailResponse transition(
      @PathVariable UUID id, @PathVariable String action, CurrentUser me) {
    CourseAction courseAction = CourseAction.valueOf(action.toUpperCase(Locale.ROOT));
    return CourseDetailResponse.from(lifecycle.transition(id, courseAction, Viewers.of(me)));
  }
}
