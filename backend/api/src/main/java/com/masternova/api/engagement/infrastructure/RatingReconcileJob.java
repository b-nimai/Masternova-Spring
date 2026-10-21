package com.masternova.api.engagement.infrastructure;

import com.masternova.api.engagement.application.RatingReconciler;
import com.masternova.api.engagement.application.RatingReconciler.Repairs;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recounts every course's rating a few minutes after each start, then daily — on ONE replica
 * (ShedLock). ⭐ The review transaction keeps the tally exact; this repairs what wrote around it
 * (ADR-0023 §4). A non-zero repair is a bug or a cascade: look at it.
 *
 * <p>Disabled with {@code masternova.engagement.reconcile-enabled=false} (the ITs run it by hand).
 */
@Component
@ConditionalOnProperty(
    name = "masternova.engagement.reconcile-enabled",
    havingValue = "true",
    matchIfMissing = true)
class RatingReconcileJob {

  private static final Logger log = LoggerFactory.getLogger(RatingReconcileJob.class);

  private final RatingReconciler reconciler;

  RatingReconcileJob(RatingReconciler reconciler) {
    this.reconciler = reconciler;
  }

  @Scheduled(
      fixedDelayString = "${masternova.engagement.reconcile-every:24h}",
      initialDelayString = "${masternova.engagement.reconcile-after:3m}")
  @SchedulerLock(name = "rating-reconcile", lockAtMostFor = "30m")
  void reconcile() {
    try {
      Repairs repairs = reconciler.reconcile();
      if (repairs.any()) {
        log.warn(
            "rating reconcile repaired {} tally(ies) and {} catalog summary(ies)",
            repairs.tallies(),
            repairs.catalog());
      }
    } catch (RuntimeException e) { // the next run tries again; never kill the scheduler thread
      log.warn("rating reconcile failed", e);
    }
  }
}
