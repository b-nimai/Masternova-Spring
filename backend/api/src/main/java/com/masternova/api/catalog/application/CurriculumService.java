package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CurriculumCommand;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.platform.ValidationException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies curriculum COMMANDS (docs/lld/catalog-authoring.md §5). The aggregate does the work and
 * hands back the inverse; this service decides who may, checks the version, and makes the edit one
 * transaction (the deferred position constraints are checked at its commit).
 */
@Service
public class CurriculumService {

  private final CourseRepository courses;
  private final CourseAccess access;
  private final Clock clock;

  CurriculumService(CourseRepository courses, CourseAccess access, Clock clock) {
    this.courses = courses;
    this.access = access;
    this.clock = clock;
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Course apply(
      UUID courseId, long expectedVersion, CurriculumCommand command, Viewer actor) {
    if (!command.clientAllowed()) {
      throw ValidationException.of(
          "command.kind", "NOT_A_CLIENT_COMMAND", "That kind of edit can only come from undo.");
    }
    Course course = access.forEditing(courseId, actor, expectedVersion);
    course.apply(command.withIds(), clock.instant());
    courses.deleteRemovedLectures(course);
    return course;
  }
}
