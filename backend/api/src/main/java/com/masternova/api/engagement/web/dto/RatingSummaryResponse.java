package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.RatingTally;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * The course's rating: the average and count (as on the catalog card) and how many learners gave
 * each number of stars, {@code stars[0]} = 1★ … {@code stars[4]} = 5★.
 */
public record RatingSummaryResponse(BigDecimal average, int count, List<Integer> stars) {

  public static RatingSummaryResponse from(RatingTally t) {
    return new RatingSummaryResponse(
        t.average(), t.count(), Arrays.stream(t.stars()).boxed().toList());
  }
}
