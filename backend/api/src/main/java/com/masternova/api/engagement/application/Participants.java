package com.masternova.api.engagement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseStanding;
import com.masternova.api.engagement.domain.CourseRef;
import com.masternova.api.enrollment.EnrollmentApi;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one access check of engagement: may this caller speak about this course? ⭐ Reviews and Q&A
 * are for a course's learners (actively enrolled), its teacher and admins, once it is OPEN:
 * published, or archived (an archived course keeps playing for those who hold it, so its Q&A and
 * reviews stay theirs). A draft or unknown course is a 404 (not a 403), so ids can't be probed (API
 * conventions §1); anyone else is 403 {@code NOT_ENROLLED}. The PUBLIC reads (the course page's
 * reviews) need it published, like the course page itself.
 *
 * <p>One small query per check ({@code CatalogApi.courseStanding}): it runs on every course-page
 * view, and the full {@code CourseFacts} would also load the curriculum.
 */
@Component
public class Participants {

  private final CatalogApi catalog;
  private final EnrollmentApi enrollments;

  Participants(CatalogApi catalog, EnrollmentApi enrollments) {
    this.catalog = catalog;
    this.enrollments = enrollments;
  }

  /** The course of the lecture, if the caller may read and post in its Q&A. */
  public CourseRef ofLecture(CurrentUser me, UUID lectureId) {
    CourseRef course = open(catalog.courseStandingOfLecture(lectureId), "Lecture", lectureId);
    if (!course.instructorId().equals(me.id())
        && !me.has(Role.ADMIN)
        && !enrollments.isEnrolled(me.id(), course.courseId())) {
      throw notEnrolled();
    }
    return course;
  }

  /**
   * Who may review: an enrolled learner who doesn't teach the course. ⭐ An admin reviews only as a
   * learner (enrolled), never by role.
   */
  public void requireReviewer(CurrentUser me, UUID courseId) {
    CourseRef course = open(catalog.courseStanding(courseId), "Course", courseId);
    if (course.instructorId().equals(me.id())) {
      throw new ForbiddenException("OWN_COURSE", "You can't review a course you teach.");
    }
    if (!enrollments.isEnrolled(me.id(), courseId)) {
      throw notEnrolled();
    }
  }

  /** For the public reads: the course must be published (as its page), else 404. */
  public void requirePublished(UUID courseId) {
    catalog
        .courseStanding(courseId)
        .filter(c -> c.availability() == Availability.PUBLISHED)
        .orElseThrow(() -> new NotFoundException("Course", courseId));
  }

  private static CourseRef open(Optional<CourseStanding> course, String resource, UUID id) {
    return course
        .filter(c -> c.availability() != Availability.UNPUBLISHED)
        .map(c -> new CourseRef(c.courseId(), c.instructorId()))
        .orElseThrow(() -> new NotFoundException(resource, id));
  }

  private static ForbiddenException notEnrolled() {
    return new ForbiddenException("NOT_ENROLLED", "Enroll in this course to take part.");
  }
}
