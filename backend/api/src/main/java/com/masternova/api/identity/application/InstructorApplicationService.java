package com.masternova.api.identity.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.domain.InstructorApplication;
import com.masternova.api.identity.domain.InstructorApplicationRepository;
import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The applicant's side of becoming an instructor: apply (from signup's Instructor tab or the
 * account page) and see where it stands. The admin's side is {@link InstructorReviewService}.
 */
@Service
public class InstructorApplicationService {

  private final InstructorApplicationRepository applications;
  private final UserRepository users;
  private final Clock clock;

  InstructorApplicationService(
      InstructorApplicationRepository applications, UserRepository users, Clock clock) {
    this.applications = applications;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public InstructorApplication apply(CurrentUser me, String pitch) {
    // ⭐ the STORED roles, not the token's: right after an approval the token still says LEARNER
    // for up to 15 minutes, and an approved instructor must not file a new application
    boolean instructor =
        users
            .findById(me.id())
            .orElseThrow(() -> new NotFoundException("User", me.id()))
            .roles()
            .contains(Role.INSTRUCTOR);
    if (instructor) {
      throw new RuleViolationException("ALREADY_INSTRUCTOR", "You can already teach.");
    }
    Optional<InstructorApplication> existing = applications.findById(me.id());
    if (existing.isEmpty()) {
      try {
        // flush NOW: two first applications at once collide on the primary key here, in our try
        return applications.saveAndFlush(
            InstructorApplication.submit(me.id(), pitch, clock.instant()));
      } catch (DataIntegrityViolationException raceLost) {
        throw alreadyPending();
      }
    }
    InstructorApplication application = existing.get();
    application.resubmit(pitch, clock.instant()); // 409 APPLICATION_PENDING while it waits
    return application;
  }

  @Transactional(readOnly = true)
  public Optional<InstructorApplication> mine(CurrentUser me) {
    return applications.findById(me.id());
  }

  private static ConflictException alreadyPending() {
    return new ConflictException(
        "APPLICATION_PENDING", "Your application is already waiting for an admin.");
  }
}
