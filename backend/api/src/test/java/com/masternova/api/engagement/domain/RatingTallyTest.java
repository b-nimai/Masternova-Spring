package com.masternova.api.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RatingTallyTest {

  @Test
  void anEmptyTallyAveragesZero() {
    RatingTally empty = RatingTally.empty();
    assertThat(empty.count()).isZero();
    assertThat(empty.sum()).isZero();
    assertThat(empty.average()).isEqualByComparingTo("0.00");
    assertThat(empty.average().scale()).isEqualTo(2);
  }

  @Test
  void countSumAndAverageAreDerivedFromTheStars() {
    RatingTally tally = RatingTally.of(1, 0, 0, 2, 3); // 1 + 8 + 15 = 24 over 6
    assertThat(tally.count()).isEqualTo(6);
    assertThat(tally.sum()).isEqualTo(24);
    assertThat(tally.average()).isEqualByComparingTo("4.00");
  }

  @Test
  void theAverageRoundsHalfUpToTwoPlaces() {
    assertThat(RatingTally.of(0, 0, 0, 1, 2).average()).isEqualByComparingTo("4.67"); // 14/3
    assertThat(RatingTally.of(0, 0, 0, 7, 1).average()).isEqualByComparingTo("4.13"); // 4.125
  }

  @Test
  void replaceMovesOneReviewBetweenCounters() {
    RatingTally tally = RatingTally.empty().add(2).add(5);
    assertThat(tally.replace(2, 4)).isEqualTo(RatingTally.of(0, 0, 0, 1, 1));
    assertThat(tally.replace(5, 5)).isEqualTo(tally); // the same rating: no change
  }

  @Test
  void aCounterNeverGoesBelowZero() {
    assertThatThrownBy(() -> RatingTally.empty().remove(3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RatingTally.of(0, 0, -1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ratingsAreOneToFiveStars() {
    assertThatThrownBy(() -> RatingTally.empty().add(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RatingTally.empty().add(6))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void itIsImmutableAndComparesByContent() {
    int[] stars = {1, 2, 3, 4, 5};
    RatingTally tally = new RatingTally(stars);
    stars[0] = 99; // the caller's array is not the tally's
    tally.stars()[1] = 99; // nor is the one it hands out
    assertThat(tally).isEqualTo(RatingTally.of(1, 2, 3, 4, 5));
    assertThat(tally.hashCode()).isEqualTo(RatingTally.of(1, 2, 3, 4, 5).hashCode());
  }

  /**
   * ⭐ The incremental tally equals a recount after any sequence of writes, edits and deletes — the
   * property the review transaction relies on (and the nightly reconciler checks in SQL).
   */
  @Test
  void incrementalUpdatesAlwaysEqualARecount() {
    Random random = new Random(11);
    for (int run = 0; run < 200; run++) {
      int[] ratingOf = new int[20]; // 20 learners; 0 = no review
      RatingTally tally = RatingTally.empty();
      for (int step = 0; step < 100; step++) {
        int learner = random.nextInt(ratingOf.length);
        int old = ratingOf[learner];
        int next = random.nextInt(6); // 0 = delete
        if (old == 0 && next != 0) {
          tally = tally.add(next);
        } else if (old != 0 && next == 0) {
          tally = tally.remove(old);
        } else if (old != 0) {
          tally = tally.replace(old, next);
        }
        ratingOf[learner] = next;
      }
      int[] recount = new int[5];
      for (int r : ratingOf) {
        if (r != 0) {
          recount[r - 1]++;
        }
      }
      assertThat(tally).isEqualTo(new RatingTally(recount));
    }
  }
}
