package com.masternova.api.catalog.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.catalog.CourseOffer;
import com.masternova.api.catalog.CourseOutline;
import com.masternova.api.catalog.LectureOutline;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CourseStatus;
import com.masternova.api.catalog.domain.LectureKind;
import com.masternova.api.catalog.domain.LectureRow;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CatalogApi} — the read side other modules use. Two statements per course's facts, one per
 * batch of offers, two per batch of outlines. Plus the numbers others write: enrollments, ratings.
 */
@Service
@Transactional(readOnly = true)
class CatalogQueries implements CatalogApi {

  private final CourseRepository courses;

  CatalogQueries(CourseRepository courses) {
    this.courses = courses;
  }

  @Override
  public Optional<CourseFacts> courseFacts(UUID courseId) {
    return courses.findFactsById(courseId).map(CatalogQueries::facts);
  }

  @Override
  public boolean courseExists(UUID courseId) {
    return courses.existsById(courseId);
  }

  @Override
  public Optional<CourseFacts> courseFactsOfLecture(UUID lectureId) {
    return courses.findCourseIdByLectureId(lectureId).flatMap(this::courseFacts);
  }

  @Override
  public List<CourseOffer> offers(Collection<UUID> courseIds) {
    return courses.findAllById(courseIds).stream() // one statement: no curriculum is loaded
        .map(
            c ->
                new CourseOffer(
                    c.id(),
                    c.slug(),
                    c.title(),
                    c.instructor().id(),
                    availability(c.status()),
                    c.price(),
                    c.priceSetAt().isPresent()))
        .toList();
  }

  @Override
  public List<CourseOutline> outlines(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<LectureOutline>> lectures =
        courses.findLectureRowsByCourseIds(courseIds).stream()
            .map(CatalogQueries::outline) // ⭐ groupingBy keeps the curriculum order within a course
            .collect(Collectors.groupingBy(LectureOutline::courseId));
    return courses.findAllById(courseIds).stream()
        .map(
            c ->
                new CourseOutline(
                    c.id(),
                    c.slug(),
                    c.title(),
                    c.instructor().name(),
                    c.status() == CourseStatus.PUBLISHED,
                    lectures.getOrDefault(c.id(), List.of())))
        .toList();
  }

  @Override
  public List<LectureOutline> lectures(Collection<UUID> lectureIds) {
    return lectureIds.isEmpty()
        ? List.of()
        : courses.findLectureRows(lectureIds).stream().map(CatalogQueries::outline).toList();
  }

  @Override
  @Transactional // joins the caller's: the counter moves with the caller's own change
  public void adjustEnrollmentCount(UUID courseId, int delta) {
    courses.adjustEnrollmentCount(courseId, delta);
  }

  @Override
  @Transactional // joins the caller's: the summary moves with the review that changed it
  public boolean updateRating(UUID courseId, int count, BigDecimal average) {
    return courses.updateRating(courseId, count, Course.checkedRatingAverage(average, count)) > 0;
  }

  private static LectureOutline outline(LectureRow r) {
    return new LectureOutline(
        r.lectureId(),
        r.courseId(),
        r.title(),
        r.kind() == LectureKind.VIDEO,
        r.duration().seconds());
  }

  private static CourseFacts facts(Course c) {
    return new CourseFacts(
        c.id(),
        c.instructor().id(),
        availability(c.status()),
        c.price().amountMinor(),
        c.priceSetAt().isPresent(),
        c.sections().stream()
            .flatMap(s -> s.lectures().stream())
            .map(l -> new LectureFacts(l.id(), l.isPreview(), l.assetId()))
            .toList());
  }

  private static Availability availability(CourseStatus status) {
    return switch (status) {
      case DRAFT, IN_REVIEW -> Availability.UNPUBLISHED;
      case PUBLISHED -> Availability.PUBLISHED;
      case ARCHIVED -> Availability.ARCHIVED;
    };
  }
}
