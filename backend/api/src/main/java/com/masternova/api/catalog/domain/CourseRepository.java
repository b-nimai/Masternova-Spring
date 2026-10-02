package com.masternova.api.catalog.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The ONE repository of the Course aggregate — no Section or Lecture repositories (LLD §3). A
 * Spring Data interface living directly in {@code domain/}; Spring generates the implementation.
 *
 * <p>{@link JpaSpecificationExecutor} is what lets the catalog search compose Specifications (5.4)
 * and scroll by keyset (5.5).
 */
public interface CourseRepository
    extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

  /**
   * The course page: course + category + sections in ONE statement (the entity graph turns them
   * into joins); the lectures follow in ONE more, batch-fetched (Section's {@code @BatchSize}). Two
   * statements whatever the curriculum size — {@code CourseQueryCountIT} keeps it that way.
   *
   * <p>⭐ "WithCurriculum" between find…By is free text for Spring Data's query derivation: the
   * query is still "by slug"; the name just says what gets fetched.
   */
  @EntityGraph(attributePaths = {"category", "sections"})
  Optional<Course> findWithCurriculumBySlug(String slug);

  @EntityGraph(attributePaths = {"category", "sections"})
  Optional<Course> findWithCurriculumById(UUID id);
}
