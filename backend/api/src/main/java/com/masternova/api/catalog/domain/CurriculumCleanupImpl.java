package com.masternova.api.catalog.domain;

import jakarta.persistence.EntityManager;

/**
 * ⭐ Spring Data finds this by NAME: a class called {@code <fragment interface>Impl} becomes part of
 * every repository that extends the fragment, so {@code CourseRepository} gains {@code
 * deleteRemovedLectures} with the EntityManager hidden here. It lives next to the interface: in
 * this Spring Data version the lookup didn't find it in {@code infrastructure/} (the repository
 * failed to start, treating the method as a derived query), so it sits beside its interface, like
 * the Spring Data repositories themselves (LLD §4).
 */
class CurriculumCleanupImpl implements CurriculumCleanup {

  private final EntityManager entityManager;

  CurriculumCleanupImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public void deleteRemovedLectures(Course course) {
    for (Lecture lecture : course.removedLectures()) {
      entityManager.remove(lecture); // a DELETE at flush, after the renumbering UPDATEs
    }
    course.forgetRemovedLectures();
  }
}
