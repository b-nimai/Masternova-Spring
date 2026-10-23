package com.masternova.api.identity.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.domain.InstructorApplication;
import com.masternova.api.identity.domain.InstructorApplicationRepository;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin's side: the queue of pending applications, approve (grants INSTRUCTOR in the same
 * transaction), reject. ADMIN-only on the SERVICE, like {@link UserAdminService}.
 */
@Service
public class InstructorReviewService {

  /** The queue is short (a handful a day); a cursor arrives when it isn't. */
  static final int QUEUE_LIMIT = 100;

  private final InstructorApplicationRepository applications;
  private final UserRepository users;
  private final Clock clock;

  InstructorReviewService(
      InstructorApplicationRepository applications, UserRepository users, Clock clock) {
    this.applications = applications;
    this.users = users;
    this.clock = clock;
  }

  /** A pending application with who sent it. */
  public record Pending(InstructorApplication application, User applicant) {}

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<Pending> pending() {
    List<InstructorApplication> queue =
        applications.findByStatusOrderByAppliedAtAsc(
            InstructorApplication.Status.PENDING, Limit.of(QUEUE_LIMIT));
    // one query for all the applicants, not one per row
    Map<UUID, User> applicants =
        users.findAllById(queue.stream().map(InstructorApplication::userId).toList()).stream()
            .collect(Collectors.toMap(User::id, Function.identity()));
    return queue.stream()
        .filter(a -> applicants.containsKey(a.userId()))
        .map(a -> new Pending(a, applicants.get(a.userId())))
        .toList();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public InstructorApplication approve(CurrentUser admin, UUID userId) {
    InstructorApplication application = find(userId);
    decide(() -> application.approve(admin.id(), clock.instant()));
    // ⭐ same transaction: approved ⇔ instructor. The applicant's next token refresh carries it.
    users
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("User", userId))
        .grant(Role.INSTRUCTOR);
    return application;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public InstructorApplication reject(CurrentUser admin, UUID userId) {
    InstructorApplication application = find(userId);
    decide(() -> application.reject(admin.id(), clock.instant()));
    return application;
  }

  private InstructorApplication find(UUID userId) {
    return applications
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("InstructorApplication", userId));
  }

  private static void decide(Runnable decision) {
    try {
      decision.run();
    } catch (IllegalStateException alreadyDecided) {
      throw new ConflictException(
          "APPLICATION_DECIDED", "This application was already decided the other way.");
    }
  }
}
