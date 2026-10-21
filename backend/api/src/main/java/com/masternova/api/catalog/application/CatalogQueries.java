package com.masternova.api.catalog.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.catalog.CourseOffer;
import com.masternova.api.catalog.CourseOutline;
import com.masternova.api.catalog.CourseStanding;
import com.masternova.api.catalog.LectureOutline;
import com.masternova.api.catalog.RatingSummary;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CourseStandingRow;
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
import org.springframework.jdbc.core.simple.JdbcClient;
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
  private final JdbcClient jdbc;

  CatalogQueries(CourseRepository courses, JdbcClient jdbc) {
    this.courses = courses;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CourseFacts> courseFacts(UUID courseId) {
    return courses.findFactsById(courseId).map(CatalogQueries::facts);
  }

  @Override
  public Optional<CourseStanding> courseStanding(UUID courseId) {
    return courses.findStandingById(courseId).map(CatalogQueries::standing);
  }

  private static CourseStanding standing(CourseStandingRow r) {
    return new CourseStanding(r.courseId(), r.instructorId(), availability(r.status()));
  }

  @Override
  public Optional<CourseStanding> courseStandingOfLecture(UUID lectureId) {
    return courses.findStandingByLectureId(lectureId).map(CatalogQueries::standing);
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

  /**
   * ⭐ One statement per batch: the rows travel as arrays through {@code unnest}, and {@code IS
   * DISTINCT FROM} touches (and counts) only the summaries that really differ.
   */
  @Override
  @Transactional // joins the caller's
  public int updateRatings(List<RatingSummary> ratings) {
    if (ratings.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            """
            UPDATE course c SET rating_count = r.n, rating_average = r.avg
            FROM unnest(:ids, :counts, :averages) AS r(id, n, avg)
            WHERE c.id = r.id AND (c.rating_count, c.rating_average) IS DISTINCT FROM (r.n, r.avg)
            """)
        .param("ids", ratings.stream().map(RatingSummary::courseId).toArray(UUID[]::new))
        .param("counts", ratings.stream().map(RatingSummary::count).toArray(Integer[]::new))
        .param(
            "averages",
            ratings.stream()
                .map(r -> Course.checkedRatingAverage(r.average(), r.count()))
                .toArray(BigDecimal[]::new))
        .update();
  }

  @Override
  public List<UUID> ratedCourses(UUID afterCourseId, int limit) {
    return courses.findRatedIds(afterCourseId == null ? null : afterCourseId.toString(), limit);
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
