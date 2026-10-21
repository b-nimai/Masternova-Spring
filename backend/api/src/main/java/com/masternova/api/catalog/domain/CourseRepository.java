package com.masternova.api.catalog.domain;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * For other modules' decisions ({@code CatalogApi}): course + sections in one statement, lectures
   * batched in a second — no category, which nobody outside catalog reads.
   */
  @EntityGraph(attributePaths = "sections")
  Optional<Course> findFactsById(UUID id);

  /** Which course a lecture belongs to (entitlement asks by lecture). One indexed join. */
  @Query("select s.course.id from Lecture l join l.section s where l.id = :lectureId")
  Optional<UUID> findCourseIdByLectureId(UUID lectureId);

  /** The lectures of these courses, in curriculum order, without loading any aggregate. */
  @Query(
      """
      select new com.masternova.api.catalog.domain.LectureRow(
          s.course.id, l.id, l.title, l.kind, l.duration)
      from Lecture l join l.section s
      where s.course.id in :courseIds
      order by s.course.id, s.position, l.position
      """)
  List<LectureRow> findLectureRowsByCourseIds(Collection<UUID> courseIds);

  /** These lectures with their course; unknown ids are absent. One statement. */
  @Query(
      """
      select new com.masternova.api.catalog.domain.LectureRow(
          s.course.id, l.id, l.title, l.kind, l.duration)
      from Lecture l join l.section s
      where l.id in :lectureIds
      """)
  List<LectureRow> findLectureRows(Collection<UUID> lectureIds);

  /**
   * ⭐ A relative update in SQL, never read-modify-write: concurrent enrollments can't lose a count,
   * and it never bumps the course's {@code version} (a learner enrolling must not make the
   * instructor's open editor stale). {@code Course.enrollmentCount} is {@code updatable = false},
   * so an instructor's save can't write a stale count back either.
   */
  @Modifying
  @Query(
      value =
          "UPDATE course SET enrollment_count = GREATEST(enrollment_count + :delta, 0) WHERE id = :id",
      nativeQuery = true)
  int adjustEnrollmentCount(UUID id, int delta);

  /**
   * ⭐ Sets the rating summary engagement computed from its locked tally (ADR-0023): absolute
   * values, joining the caller's transaction, never bumping {@code version} (a review is not an
   * edit). {@code IS DISTINCT FROM}: a value already right matches nothing, so the reconciler can
   * count what it really repaired.
   */
  @Modifying
  @Query(
      value =
          """
          UPDATE course SET rating_count = :count, rating_average = :average
          WHERE id = :id AND (rating_count, rating_average) IS DISTINCT FROM (:count, :average)
          """,
      nativeQuery = true)
  int updateRating(UUID id, int count, BigDecimal average);
}
