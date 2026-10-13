package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseAction;
import com.masternova.api.catalog.domain.PublishCheck;
import com.masternova.api.catalog.domain.PublishGate;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.media.AssetReadiness;
import com.masternova.api.media.MediaApi;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final MediaApi media;
  private final Clock clock;

  CourseLifecycleService(CourseAccess access, MediaApi media, Clock clock) {
    this.access = access;
    this.media = media;
    this.clock = clock;
  }

  /** The wizard's checklist: every publish requirement with its current result. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional(readOnly = true)
  public List<PublishCheck> readiness(UUID courseId, Viewer actor) {
    Course course = access.forAuthoring(courseId, actor);
    return PublishGate.evaluate(course, readyAssets(course));
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
    gatedTransition(course, action);
    return course; // managed: dirty checking writes it, and @Version bumps, at commit
  }

  /** ⭐ The review step is real: only an ADMIN (the reviewer) publishes, and the gate re-runs. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public Course publish(UUID courseId, Viewer reviewer) {
    Course course = access.forAuthoring(courseId, reviewer);
    gatedTransition(course, CourseAction.PUBLISH);
    return course;
  }

  /**
   * ⭐ Catalog ASKS media at the moment it decides (media LLD §4) — no copied status to go stale. A
   * gated edge also adopts the probed durations, so the catalog shows the real lengths.
   */
  private void gatedTransition(Course course, CourseAction action) {
    Set<UUID> ready = Set.of();
    if (action.isGated()) {
      Map<UUID, AssetReadiness> readiness = media.readiness(course.videoAssetIds());
      Map<UUID, Integer> durations = new HashMap<>();
      readiness.forEach((id, r) -> r.durationSeconds().ifPresent(d -> durations.put(id, d)));
      course.adoptMediaDurations(durations);
      ready = readyOf(readiness);
    }
    course.transition(action, clock.instant(), ready);
  }

  private Set<UUID> readyAssets(Course course) {
    return readyOf(media.readiness(course.videoAssetIds()));
  }

  private static Set<UUID> readyOf(Map<UUID, AssetReadiness> readiness) {
    return readiness.entrySet().stream()
        .filter(e -> e.getValue().ready())
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
