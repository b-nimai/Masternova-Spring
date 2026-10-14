package com.masternova.api.catalog.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.catalog.CourseOffer;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CourseStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CatalogApi} — the read side other modules use. Two statements per course's facts, one per
 * batch of offers.
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
