package com.masternova.api.identity.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Keyed by the applicant's user id (one application per user). */
public interface InstructorApplicationRepository
    extends JpaRepository<InstructorApplication, UUID> {

  /**
   * ⭐ {@code SELECT … FOR UPDATE}: two overlapping decisions on one application queue here, so the
   * second reads the first's outcome (an approve twice → 200 twice, not a version conflict).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from InstructorApplication a where a.userId = :userId")
  Optional<InstructorApplication> findForDecision(UUID userId);

  /**
   * The admin's queue, first page: oldest first (the partial index
   * instructor_application_pending_idx).
   */
  List<InstructorApplication> findByStatusOrderByAppliedAtAscUserIdAsc(
      InstructorApplication.Status status, Limit limit);

  /** The next page: the pending rows after {@code (at, id)} in the same order (a keyset). */
  @Query(
      """
      select a from InstructorApplication a
      where a.status = com.masternova.api.identity.domain.InstructorApplication.Status.PENDING
        and (a.appliedAt > :at or (a.appliedAt = :at and a.userId > :id))
      order by a.appliedAt, a.userId
      """)
  List<InstructorApplication> findPendingAfter(Instant at, UUID id, Limit limit);
}
