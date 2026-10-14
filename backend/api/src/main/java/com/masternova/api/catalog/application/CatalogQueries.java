package com.masternova.api.catalog.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CatalogApi} — the read side other modules use. Two statements per course. */
@Service
@Transactional(readOnly = true)
class CatalogQueries implements CatalogApi {

  private final CourseRepository courses;

  CatalogQueries(CourseRepository courses) {
    this.courses = courses;
  }

  @Override
  public Optional<CourseFacts> courseFacts(UUID courseId) {
    return courses.findWithCurriculumById(courseId).map(CatalogQueries::facts);
  }

  @Override
  public Optional<CourseFacts> courseFactsOfLecture(UUID lectureId) {
    return courses.findCourseIdByLectureId(lectureId).flatMap(this::courseFacts);
  }

  private static CourseFacts facts(Course c) {
    return new CourseFacts(
        c.id(),
        c.instructor().id(),
        switch (c.status()) {
          case DRAFT, IN_REVIEW -> Availability.UNPUBLISHED;
          case PUBLISHED -> Availability.PUBLISHED;
          case ARCHIVED -> Availability.ARCHIVED;
        },
        c.price().amountMinor(),
        c.priceSetAt().isPresent(),
        c.sections().stream()
            .flatMap(s -> s.lectures().stream())
            .map(l -> new LectureFacts(l.id(), l.isPreview(), l.assetId().orElse(null)))
            .toList());
  }
}
