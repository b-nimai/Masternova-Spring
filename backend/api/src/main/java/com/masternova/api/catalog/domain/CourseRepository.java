package com.masternova.api.catalog.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * The ONE repository of the Course aggregate — no Section or Lecture repositories (LLD §3). A
 * Spring Data interface living directly in {@code domain/}; Spring generates the implementation.
 *
 * <p>{@link JpaSpecificationExecutor} is what lets the catalog search compose Specifications (5.4)
 * and scroll by keyset (5.5).
 */
public interface CourseRepository
    extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course>, CurriculumCleanup {

  /**
   * ⭐ The FIRST statement of every content write: {@code SELECT … FOR UPDATE} on the course row.
   * Held for the edit's few milliseconds (never across a user's think time), it serialises editors
   * of one course. Without it, two concurrent edits each inserted their rows, then deadlocked: one
   * waiting at commit on the other's uncommitted row (the deferred position constraint), the other
   * waiting on the first's course-row lock (found by CurriculumIT; ADR-0010 note).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Course c where c.id = :id")
  Optional<Course> lockById(UUID id);

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
