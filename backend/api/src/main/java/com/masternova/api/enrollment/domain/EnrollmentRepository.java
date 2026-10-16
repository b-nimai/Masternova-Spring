package com.masternova.api.enrollment.domain;

import com.masternova.api.entitlement.EntitlementSource;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The enrollment rows. ⭐ Every write is ONE statement that is idempotent and order-independent: it
 * applies only if its event is newer than the row's {@code status_at}, so a listener running twice,
 * late or concurrently with the opposite event can't flip a row the wrong way (ADR-0021).
 */
public interface EnrollmentRepository {

  Optional<Enrollment> find(UUID userId, UUID courseId);

  /** A learner's ACTIVE enrollments, most recently watched first, then newest first. */
  List<Enrollment> active(UUID userId);

  /**
   * Enrolls at {@code at}: a new row, or a REVOKED one (a tombstone included) whose status is
   * older.
   *
   * @return whether the learner became ACTIVE (the course's counter moves only then)
   */
  boolean enroll(UUID userId, UUID courseId, EntitlementSource source, Instant at);

  /**
   * Ends the enrollment at {@code at} if it is ACTIVE and older; with no row at all, leaves a
   * REVOKED tombstone so an older grant arriving later can't enroll.
   *
   * @return whether an ACTIVE enrollment ended (the course's counter moves only then)
   */
  boolean revoke(UUID userId, UUID courseId, Instant at);

  /** Which of these pairs are ACTIVE enrollments — the flush keeps progress only for those. */
  Set<UserCourse> activeAmong(Collection<UserCourse> pairs);

  /**
   * Recomputes these enrollments' rollups from their progress rows: completed lectures, the last
   * lecture watched and when. ⭐ A RECOUNT, not {@code +1}: a replayed flush can't double-count.
   */
  void refreshRollups(Collection<UserCourse> pairs);
}
