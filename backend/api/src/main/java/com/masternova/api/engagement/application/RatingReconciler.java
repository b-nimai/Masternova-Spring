package com.masternova.api.engagement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.RatingSummary;
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
 * with the average it loaded, a rating no review ever explained (written by SQL, or seeded).
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
    for (UUID courseId : reviews.drifted()) { // candidates only: re-checked under the lock
      if (repair(courseId).tally()) {
        tallies++;
      }
    }
    int catalogRepairs = pushTallies();
    for (UUID courseId : untallied()) {
      if (repair(courseId).catalog()) {
        catalogRepairs++;
      }
    }
    return new Repairs(tallies, catalogRepairs);
  }

  private record Repaired(boolean tally, boolean catalog) {}

  /**
   * ⭐ THE repair, for one course in one transaction: lock the tally (creating an empty one if there
   * is none), recount — a new statement, so it sees what committed while we waited — save it if it
   * differs, and set catalog's summary from it.
   */
  private Repaired repair(UUID courseId) {
    return tx.execute(
        s -> {
          RatingTally stored = reviews.lockTally(courseId);
          RatingTally truth = reviews.recount(courseId);
          boolean tally = !truth.equals(stored);
          if (tally) {
            reviews.saveTally(courseId, truth);
          }
          return new Repaired(
              tally, catalog.updateRating(courseId, truth.count(), truth.average()));
        });
  }

  /**
   * Catalog's columns agree with every tally: a page of LOCKED tallies per transaction (a review
   * can't change one between the read and the push), pushed in one statement.
   */
  private int pushTallies() {
    int repaired = 0;
    UUID after = null;
    while (true) {
      UUID from = after;
      Page page = tx.execute(s -> push(reviews.lockTallies(from, PAGE)));
      repaired += page.changed();
      if (page.size() < PAGE) {
        return repaired;
      }
      after = page.last();
    }
  }

  private record Page(int size, int changed, UUID last) {}

  private Page push(List<CourseTally> rows) {
    int changed =
        catalog.updateRatings(
            rows.stream()
                .map(r -> new RatingSummary(r.courseId(), r.tally().count(), r.tally().average()))
                .toList());
    return new Page(rows.size(), changed, rows.isEmpty() ? null : rows.getLast().courseId());
  }

  /**
   * Courses catalog shows a rating for, with no tally to explain it ("4.7 (1,500)", no reviews).
   */
  private List<UUID> untallied() {
    List<UUID> found = new java.util.ArrayList<>();
    UUID after = null;
    while (true) {
      List<UUID> rated = catalog.ratedCourses(after, PAGE);
      found.addAll(reviews.withoutTally(rated));
      if (rated.size() < PAGE) {
        return found;
      }
      after = rated.getLast();
    }
  }
}
