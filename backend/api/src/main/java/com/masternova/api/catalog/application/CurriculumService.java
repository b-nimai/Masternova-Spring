package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseEditLog;
import com.masternova.api.catalog.domain.CourseEditLog.Entry;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CurriculumCommand;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.media.MediaApi;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Curriculum edits, undo and redo (docs/lld/catalog-authoring.md §5) — the INVOKER of the Command
 * pattern. Each method is ONE transaction: lock the course row → check who and which version →
 * apply → delete removed lectures → write the history → commit (deferred position constraints and
 * {@code @Version} checked there). An edit and its history row commit together or not at all.
 */
@Service
public class CurriculumService {

  /** The curriculum plus what the editor's undo/redo buttons need. */
  public record Result(Course course, boolean canUndo, boolean canRedo) {}

  private final CourseRepository courses;
  private final CourseAccess access;
  private final CourseEditLog history;
  private final MediaApi media;
  private final Clock clock;

  CurriculumService(
      CourseRepository courses,
      CourseAccess access,
      CourseEditLog history,
      MediaApi media,
      Clock clock) {
    this.courses = courses;
    this.access = access;
    this.history = history;
    this.media = media;
    this.clock = clock;
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional(readOnly = true)
  public Result get(UUID courseId, Viewer actor) {
    return result(access.forAuthoring(courseId, actor));
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Result apply(
      UUID courseId, long expectedVersion, CurriculumCommand command, Viewer actor, UUID actorId) {
    if (!command.clientAllowed()) {
      throw ValidationException.of(
          "command.kind", "NOT_A_CLIENT_COMMAND", "That kind of edit can only come from undo.");
    }
    Course course = access.forEditing(courseId, actor, expectedVersion);
    // ⭐ only the course's own instructor's videos — even when an admin does the attaching
    if (command instanceof CurriculumCommand.AttachMedia(var lectureId, var assetId)
        && assetId != null
        && !media.isOwnedBy(assetId, course.instructor().id())) {
      throw ValidationException.of(
          "command.assetId", "UNKNOWN_ASSET", "That video doesn't exist or isn't yours.");
    }
    Instant now = clock.instant();
    CurriculumCommand withIds = command.withServerIds(); // ⭐ stored WITH ids: a redo recreates them
    CurriculumCommand inverse = course.apply(withIds, now);
    courses.deleteRemovedLectures(course);
    history.discardRedo(courseId); // a new edit ends the redo branch
    history.record(courseId, withIds, inverse, expectedVersion + 1, actorId, now);
    return result(course);
  }

  /** Reverses the newest done edit by applying its stored inverse. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Result undo(UUID courseId, long expectedVersion, Viewer actor) {
    Course course = access.forEditing(courseId, actor, expectedVersion);
    Entry edit =
        history
            .lastDone(courseId)
            .orElseThrow(
                () -> new ConflictException("NOTHING_TO_UNDO", "There's nothing to undo."));
    Instant now = clock.instant();
    course.apply(edit.inverse(), now);
    courses.deleteRemovedLectures(course);
    history.markUndone(edit.id(), now);
    return result(course);
  }

  /** Re-applies the oldest undone edit (its stored command, same ids). */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Result redo(UUID courseId, long expectedVersion, Viewer actor) {
    Course course = access.forEditing(courseId, actor, expectedVersion);
    Entry edit =
        history
            .firstUndone(courseId)
            .orElseThrow(
                () -> new ConflictException("NOTHING_TO_REDO", "There's nothing to redo."));
    CurriculumCommand freshInverse = course.apply(edit.command(), clock.instant());
    courses.deleteRemovedLectures(course);
    history.markRedone(edit.id(), freshInverse);
    return result(course);
  }

  private Result result(Course course) {
    return new Result(course, history.canUndo(course.id()), history.canRedo(course.id()));
  }
}
