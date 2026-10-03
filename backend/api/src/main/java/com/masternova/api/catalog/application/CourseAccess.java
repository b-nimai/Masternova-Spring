package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * "Load this course for authoring, if this actor may author it" — the one ownership rule every
 * write use case shares (duplicate, lifecycle, details, curriculum).
 *
 * <p>⭐ A course you may not author is the SAME 404 as a course that doesn't exist: a 403 would
 * confirm the id is real (an enumeration oracle). Callers run inside their own transaction.
 */
@Component
class CourseAccess {

  private final CourseRepository courses;

  CourseAccess(CourseRepository courses) {
    this.courses = courses;
  }

  /** The course with its whole curriculum loaded (the gate and the commands need it). */
  Course forAuthoring(UUID courseId, Viewer actor) {
    Course course =
        courses
            .findWithCurriculumById(courseId)
            .filter(c -> mayAuthor(actor, c))
            .orElseThrow(() -> new NotFoundException("Course", courseId));
    course.sections().forEach(section -> section.lectures().size()); // one batched query
    return course;
  }

  /**
   * ⭐ For a CONTENT write (details, pricing, curriculum): loaded, editable (not ARCHIVED), and at
   * the version the client last saw — otherwise 409 {@code VERSION_CONFLICT} with both versions
   * (ADR-0010). Two requests that pass this check together are still separated by {@code @Version}
   * at flush.
   */
  Course forEditing(UUID courseId, Viewer actor, long expectedVersion) {
    Course course = forAuthoring(courseId, actor);
    course.requireEditable();
    if (course.version() != expectedVersion) {
      throw ConflictException.versionConflict(expectedVersion, course.version());
    }
    return course;
  }

  static boolean mayAuthor(Viewer actor, Course course) {
    return switch (actor) {
      case Viewer.Admin _ -> true;
      case Viewer.Member(UUID id) -> course.isOwnedBy(id);
      case Viewer.Anonymous _ -> false;
    };
  }
}
