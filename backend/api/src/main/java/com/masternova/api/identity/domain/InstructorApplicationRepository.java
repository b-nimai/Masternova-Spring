package com.masternova.api.identity.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by the applicant's user id (one application per user). */
public interface InstructorApplicationRepository
    extends JpaRepository<InstructorApplication, UUID> {

  /** The admin's queue, oldest first (the partial index instructor_application_pending_idx). */
  List<InstructorApplication> findByStatusOrderByAppliedAtAsc(
      InstructorApplication.Status status, Limit limit);
}
