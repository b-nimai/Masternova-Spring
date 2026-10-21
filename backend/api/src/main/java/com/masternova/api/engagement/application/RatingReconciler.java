package com.masternova.api.engagement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.engagement.domain.ReviewRepository;
import com.masternova.api.engagement.domain.ReviewRepository.CourseTally;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The net under the exact tally (ADR-0023 §4): repairs what was written AROUND the review code — a
 * user's deletion cascading to their reviews, a manual SQL fix, a pre-Phase-11 pod saving a course
 * with the average it loaded.
 *
 * <p>⭐ Never repairs from a snapshot: each course is fixed in its own transaction that takes the
 * same lock as a review write and recounts AFTER it holds it, so a review committed meanwhile is
 * counted, not erased.
 */
@Service
public class RatingReconciler {

  static final int PAGE = 500;

  private final ReviewRepository reviews;
  private final CatalogApi catalog;
  private final TransactionOperations tx;

  RatingReconciler(ReviewRepository reviews, CatalogApi catalog, TransactionOperations tx) {
    this.reviews = reviews;
    this.catalog = catalog;
    this.tx = tx;
  }

  /** What one run repaired. Anything but zeros is worth a WARN: something wrote around the code. */
  public record Repairs(int tallies, int catalog) {
    public boolean any() {
      return tallies > 0 || catalog > 0;
    }
  }

  public Repairs reconcile() {
    int tallies = 0;
    for (UUID courseId : reviews.drifted()) { // a candidate list: re-checked under the lock
      if (Boolean.TRUE.equals(tx.execute(s -> repairTally(courseId)))) {
        tallies++;
      }
    }
    return new Repairs(tallies, repairCatalog());
  }

  private boolean repairTally(UUID courseId) {
    RatingTally stored = reviews.lockTally(courseId);
    RatingTally truth = reviews.recount(courseId); // ⭐ a new statement: sees what committed
    if (truth.equals(stored)) {
      return false; // drifted no more: a review write fixed it, or it was a false candidate
    }
    reviews.saveTally(courseId, truth);
    catalog.updateRating(courseId, truth.count(), truth.average());
    return true;
  }

  /**
   * Catalog's columns agree with every tally: a page of locked tallies per transaction, pushed with
   * {@code IS DISTINCT FROM} so only real differences count.
   */
  private int repairCatalog() {
    int repaired = 0;
    UUID after = null;
    while (true) {
      UUID from = after;
      int[] changed = {0};
      List<CourseTally> page =
          tx.execute(
              s -> {
                List<CourseTally> rows = reviews.lockTallies(from, PAGE);
                for (CourseTally row : rows) {
                  RatingTally t = row.tally();
                  if (catalog.updateRating(row.courseId(), t.count(), t.average())) {
                    changed[0]++;
                  }
                }
                return rows;
              });
      repaired += changed[0];
      if (page == null || page.size() < PAGE) {
        return repaired;
      }
      after = page.getLast().courseId();
    }
  }
}
