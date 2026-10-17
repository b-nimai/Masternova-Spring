package com.masternova.api.enrollment.infrastructure;

import com.masternova.api.enrollment.application.EnrollmentService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Brings enrollment back in line with entitlement, a few minutes after each start and then daily —
 * on ONE replica (ShedLock). ⭐ The listener is the path; this is the net under it: during the Phase
 * 10 rollout, pods without the listener still grant and revoke, and their events reach no one
 * (V23's backfill ran before them). A non-zero repair after that is a lost event: look at it.
 *
 * <p>Disabled with {@code masternova.enrollment.reconcile-enabled=false} (the ITs).
 */
@Component
@ConditionalOnProperty(
    name = "masternova.enrollment.reconcile-enabled",
    havingValue = "true",
    matchIfMissing = true)
class EnrollmentReconcileJob {

  private static final Logger log = LoggerFactory.getLogger(EnrollmentReconcileJob.class);

  private final EnrollmentService enrollments;

  EnrollmentReconcileJob(EnrollmentService enrollments) {
    this.enrollments = enrollments;
  }

  @Scheduled(
      fixedDelayString = "${masternova.enrollment.reconcile-every:24h}",
      initialDelayString = "${masternova.enrollment.reconcile-after:2m}")
  @SchedulerLock(name = "enrollment-reconcile", lockAtMostFor = "10m")
  void reconcile() {
    try {
      int repaired = enrollments.reconcile();
      if (repaired > 0) {
        log.warn("enrollment reconcile repaired {} enrollment(s) no listener had seen", repaired);
      }
    } catch (RuntimeException e) { // the next run tries again; never kill the scheduler thread
      log.warn("enrollment reconcile failed", e);
    }
  }
}
