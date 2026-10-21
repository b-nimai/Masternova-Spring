package com.masternova.api.engagement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.engagement.domain.Participant;
import com.masternova.api.engagement.domain.Participant.CourseRef;
import com.masternova.api.enrollment.EnrollmentApi;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one access check of engagement: who is this caller to this course? ⭐ Reviews and Q&A are for
 * a PUBLISHED course's learners and teacher; an unpublished or unknown course is a 404 (not a 403),
 * so ids can't be probed (API conventions §1).
 */
@Component
public class Participants {

  private final CatalogApi catalog;
  private final EnrollmentApi enrollments;

  Participants(CatalogApi catalog, EnrollmentApi enrollments) {
    this.catalog = catalog;
    this.enrollments = enrollments;
  }

  /** The caller's capacity in a published course, or 403 {@code NOT_ENROLLED}. */
  public Participant ofCourse(CurrentUser me, UUID courseId) {
    return of(me, published(catalog.courseFacts(courseId), "Course", courseId));
  }

  /** The same for the course a lecture belongs to. */
  public Participant ofLecture(CurrentUser me, UUID lectureId) {
    return of(me, published(catalog.courseFactsOfLecture(lectureId), "Lecture", lectureId));
  }

  /**
   * Who may review: an enrolled learner who doesn't teach the course. ⭐ An admin reviews only as a
   * learner (enrolled), never by role.
   */
  public Participant.Learner reviewer(CurrentUser me, UUID courseId) {
    CourseRef course = published(catalog.courseFacts(courseId), "Course", courseId);
    if (course.instructorId().equals(me.id())) {
      throw new ForbiddenException("OWN_COURSE", "You can't review a course you teach.");
    }
    if (!enrollments.isEnrolled(me.id(), courseId)) {
      throw notEnrolled();
    }
    return new Participant.Learner(me.id(), course);
  }

  /** The course must be published to be reviewed or discussed; its id and teacher, else 404. */
  public CourseRef publishedCourse(UUID courseId) {
    return published(catalog.courseFacts(courseId), "Course", courseId);
  }

  private Participant of(CurrentUser me, CourseRef course) {
    if (course.instructorId().equals(me.id())) {
      return new Participant.Instructor(me.id(), course);
    }
    if (enrollments.isEnrolled(me.id(), course.courseId())) {
      return new Participant.Learner(me.id(), course);
    }
    if (me.has(Role.ADMIN)) {
      return new Participant.Admin(me.id(), course);
    }
    throw notEnrolled();
  }

  private static CourseRef published(Optional<CourseFacts> facts, String resource, UUID id) {
    return facts
        .filter(f -> f.availability() == Availability.PUBLISHED)
        .map(f -> new CourseRef(f.courseId(), f.instructorId()))
        .orElseThrow(() -> new NotFoundException(resource, id));
  }

  private static ForbiddenException notEnrolled() {
    return new ForbiddenException("NOT_ENROLLED", "Enroll in this course to take part.");
  }
}
