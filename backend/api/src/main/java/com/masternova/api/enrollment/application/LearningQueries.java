package com.masternova.api.enrollment.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseOutline;
import com.masternova.api.catalog.LectureOutline;
import com.masternova.api.enrollment.domain.Completion;
import com.masternova.api.enrollment.domain.Enrollment;
import com.masternova.api.enrollment.domain.EnrollmentRepository;
import com.masternova.api.enrollment.domain.ProgressStore;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import com.masternova.api.enrollment.domain.Resume;
import com.masternova.api.platform.NotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The learner's side of reading progress: "My Learning", one course's ticks and its Continue, and
 * one lecture's resume point.
 *
 * <p>⭐ Two freshnesses on purpose (ADR-0022): the dashboard reads the enrollment rollups (≤ 30 s
 * behind, one row per course); the course page and the player read through the buffer, so a
 * heartbeat sent a second ago already counts. No transaction: each read is one statement or one
 * Redis call, and a resume point a few ms stale is still right.
 */
@Service
public class LearningQueries {

  /** A card on the dashboard. {@code continueLectureId} empty = nothing left to watch. */
  public record LearningCourse(
      CourseOutline course,
      int completedLectures,
      int trackableLectures,
      int percent,
      Instant enrolledAt,
      Optional<Instant> lastActivityAt,
      Optional<UUID> continueLectureId) {}

  /** One course for its learner: what is done, and where Continue goes. */
  public record CourseProgress(
      UUID courseId,
      boolean enrolled,
      int percent,
      Set<UUID> completedLectureIds,
      Optional<UUID> continueLectureId,
      int continuePositionSeconds) {}

  /** Where the player starts a lecture. */
  public record LectureResume(
      UUID lectureId, int positionSeconds, int maxPositionSeconds, boolean completed) {}

  private final EnrollmentRepository enrollments;
  private final ProgressStore progress;
  private final CatalogApi catalog;

  LearningQueries(EnrollmentRepository enrollments, ProgressStore progress, CatalogApi catalog) {
    this.enrollments = enrollments;
    this.progress = progress;
    this.catalog = catalog;
  }

  /**
   * "My Learning": four statements for any number of courses — the enrollments (one index scan),
   * their outlines (two), and the last lecture of each (one, plus the buffer).
   */
  public List<LearningCourse> dashboard(UUID userId) {
    List<Enrollment> active = enrollments.active(userId);
    Map<UUID, CourseOutline> outlines =
        catalog.outlines(active.stream().map(Enrollment::courseId).toList()).stream()
            .collect(Collectors.toMap(CourseOutline::courseId, Function.identity()));
    Map<UUID, ProgressUpdate> last =
        progress.find(
            userId, active.stream().flatMap(e -> e.lastLectureId().stream()).distinct().toList());
    return active.stream()
        .filter(e -> outlines.containsKey(e.courseId())) // a deleted course: the FK cascades soon
        .map(e -> card(e, outlines.get(e.courseId()), last))
        .toList();
  }

  /** One course's progress, read through the buffer. Not enrolled → {@code enrolled: false}. */
  public CourseProgress course(UUID userId, UUID courseId) {
    CourseOutline outline =
        catalog.outlines(List.of(courseId)).stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Course", courseId));
    if (enrollments.find(userId, courseId).filter(Enrollment::isActive).isEmpty()) {
      if (!outline.published()) { // ⭐ drafts are invisible to everyone not enrolled
        throw new NotFoundException("Course", courseId);
      }
      return new CourseProgress(courseId, false, 0, Set.of(), Optional.empty(), 0);
    }
    List<LectureOutline> trackable = trackable(outline);
    Map<UUID, LectureOutline> byId = byId(trackable);
    Map<UUID, ProgressUpdate> rows = progress.find(userId, byId.keySet());
    Set<UUID> completed =
        trackable.stream()
            .filter(l -> isComplete(rows.get(l.lectureId()), l))
            .map(LectureOutline::lectureId)
            .collect(Collectors.toUnmodifiableSet());
    Optional<UUID> last =
        rows.values().stream()
            .max(Comparator.comparing(ProgressUpdate::at))
            .map(ProgressUpdate::lectureId);
    Optional<UUID> next =
        Resume.lecture(
            ids(trackable), last, last.filter(completed::contains).isPresent(), completed.size());
    int position = next.map(id -> resumeAt(rows.get(id), byId.get(id))).orElse(0);
    return new CourseProgress(
        courseId,
        true,
        Completion.percent(completed.size(), trackable.size()),
        completed,
        next,
        position);
  }

  /** Where the player starts this lecture; a lecture never watched starts at 0. */
  public LectureResume lecture(UUID userId, UUID lectureId) {
    LectureOutline lecture =
        catalog.lectures(List.of(lectureId)).stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Lecture", lectureId));
    ProgressUpdate row = progress.find(userId, List.of(lectureId)).get(lectureId);
    return row == null
        ? new LectureResume(lectureId, 0, 0, false)
        : new LectureResume(
            lectureId,
            resumeAt(row, lecture),
            row.maxPositionSeconds(),
            Completion.isTrackable(lecture.video(), lecture.durationSeconds())
                && isComplete(row, lecture));
  }

  private static LearningCourse card(
      Enrollment e, CourseOutline outline, Map<UUID, ProgressUpdate> last) {
    List<LectureOutline> trackable = trackable(outline);
    Map<UUID, LectureOutline> byId = byId(trackable);
    boolean lastComplete =
        e.lastLectureId()
            .filter(id -> byId.containsKey(id) && isComplete(last.get(id), byId.get(id)))
            .isPresent();
    // the rollup may count a lecture the curriculum has since dropped: clamp to what exists
    int completed = Math.min(e.completedLectures(), trackable.size());
    return new LearningCourse(
        outline,
        completed,
        trackable.size(),
        Completion.percent(completed, trackable.size()),
        e.enrolledAt(),
        e.lastActivityAt(),
        Resume.lecture(ids(trackable), e.lastLectureId(), lastComplete, completed));
  }

  private static List<LectureOutline> trackable(CourseOutline outline) {
    return outline.lectures().stream()
        .filter(l -> Completion.isTrackable(l.video(), l.durationSeconds()))
        .toList();
  }

  private static List<UUID> ids(List<LectureOutline> lectures) {
    return lectures.stream().map(LectureOutline::lectureId).toList();
  }

  private static Map<UUID, LectureOutline> byId(List<LectureOutline> lectures) {
    return lectures.stream()
        .collect(Collectors.toMap(LectureOutline::lectureId, Function.identity()));
  }

  /** For a trackable lecture: callers filter with {@link #trackable} first. */
  private static boolean isComplete(ProgressUpdate row, LectureOutline lecture) {
    return row != null
        && Completion.isComplete(row.maxPositionSeconds(), lecture.durationSeconds());
  }

  private static int resumeAt(ProgressUpdate row, LectureOutline lecture) {
    return row == null ? 0 : Resume.position(row.lastPositionSeconds(), lecture.durationSeconds());
  }
}
