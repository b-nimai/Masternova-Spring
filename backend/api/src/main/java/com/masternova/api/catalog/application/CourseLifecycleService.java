package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseAction;
import com.masternova.api.catalog.domain.PublishCheck;
import com.masternova.api.catalog.domain.PublishGate;
import com.masternova.api.catalog.domain.Viewer;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moving a course through its lifecycle. ⭐ THIN on purpose: the rules (which transitions exist,
 * which are gated, what "ready" means) live in the aggregate ({@code Course.transition}, {@code
 * CourseState}, {@code PublishGate}). This class only decides WHO may ask, loads, and lets the
 * transaction commit — where {@code @Version} rejects a concurrent second transition (ADR-0010).
 */
@Service
public class CourseLifecycleService {

  private final CourseAccess access;
  private final Clock clock;

  CourseLifecycleService(CourseAccess access, Clock clock) {
    this.access = access;
    this.clock = clock;
  }

  /** The wizard's checklist: every publish requirement with its current result. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional(readOnly = true)
  public List<PublishCheck> readiness(UUID courseId, Viewer actor) {
    return PublishGate.evaluate(access.forAuthoring(courseId, actor));
  }

  /**
   * The author's actions: submit, withdraw, unpublish, archive. PUBLISH is the reviewer's ({@link
   * #publish}), so an instructor can't approve their own course.
   */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Course transition(UUID courseId, CourseAction action, Viewer actor) {
    if (action == CourseAction.PUBLISH) {
      throw new IllegalArgumentException("publishing is a reviewer action: use publish()");
    }
    Course course = access.forAuthoring(courseId, actor);
    course.transition(action, clock.instant());
    return course; // managed: dirty checking writes it, and @Version bumps, at commit
  }

  /** ⭐ The review step is real: only an ADMIN (the reviewer) publishes, and the gate re-runs. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public Course publish(UUID courseId, Viewer reviewer) {
    Course course = access.forAuthoring(courseId, reviewer);
    course.transition(CourseAction.PUBLISH, clock.instant());
    return course;
  }
}
