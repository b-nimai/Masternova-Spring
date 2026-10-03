package com.masternova.api.catalog.domain;

/**
 * A Spring Data CUSTOM FRAGMENT of {@link CourseRepository}: deletes the lectures an edit removed.
 * Needed because {@code Section.lectures} has no orphanRemoval (moving a lecture between sections
 * would otherwise delete it). Implemented in infrastructure with the EntityManager.
 */
public interface CurriculumCleanup {

  /** Deletes every lecture {@code course} removed since the last call. */
  void deleteRemovedLectures(Course course);
}
