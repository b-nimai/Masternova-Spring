package com.masternova.api.media.domain;

import static com.masternova.api.media.domain.PartPlan.MIB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.platform.RuleViolationException;
import com.masternova.storage.StoredPart;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PartPlanTest {

  @ParameterizedTest(name = "{0} B → {2} × {1} B")
  @CsvSource({
    "1,                5242880,   1", // one tiny file: one part, below the 5 MiB minimum (it's the
    // last)
    "5242880,          5242880,   1",
    "5242881,          5242880,   2", // one byte over → a 1-byte last part
    "200000000,        5242880,  39",
    "524288000,        5242880, 100", // 500 MiB: exactly 100 minimum parts
    "524288001,        6291456,  84", // one byte more → parts grow to 6 MiB instead of a 101st part
    "10737418240,    108003328, 100" // 10 GiB: 100 × 103 MiB
  })
  void planBoundaries(long size, long partSize, int partCount) {
    PartPlan plan = PartPlan.forSize(size);
    assertThat(plan.partSize()).isEqualTo(partSize);
    assertThat(plan.partCount()).isEqualTo(partCount).isLessThanOrEqualTo(PartPlan.MAX_PARTS);
    assertThat(plan.partSize() % MIB).isZero();
    long total = 0;
    for (int n = 1; n <= plan.partCount(); n++) {
      total += plan.expectedSize(n);
    }
    assertThat(total).isEqualTo(size); // ⭐ the parts add up exactly
  }

  @Test
  void moreThanTenGibIsARuleViolation() {
    assertThatThrownBy(() -> PartPlan.forSize(PartPlan.MAX_SIZE + 1))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.code()).isEqualTo("UPLOAD_TOO_LARGE"));
  }

  @Test
  void anImpossiblePlanCannotBeConstructed() {
    assertThatThrownBy(() -> new PartPlan(100 * MIB, 5 * MIB, 3))
        .isInstanceOf(IllegalArgumentException.class); // 3 × 5 MiB can't hold 100 MiB
    assertThatThrownBy(() -> new PartPlan(10 * MIB, 4 * MIB, 3))
        .isInstanceOf(IllegalArgumentException.class); // below S3's 5 MiB minimum
  }

  @Test
  void checkFindsMissingWrongSizedAndUnexpectedParts() {
    PartPlan plan = PartPlan.forSize(12 * MIB); // 5 + 5 + 2 MiB
    List<StoredPart> stored =
        List.of(
            new StoredPart(1, 5 * MIB, "a"),
            new StoredPart(3, 1 * MIB, "c"), // the last part, but too short
            new StoredPart(4, 1, "d")); // there is no part 4

    var check = plan.check(stored);

    assertThat(check.missing()).containsExactly(2);
    assertThat(check.wrongSize()).containsExactly(3);
    assertThat(check.unexpected()).containsExactly(4);
    assertThat(check.complete()).isFalse();
    assertThat(check.toProblem().code()).isEqualTo("UPLOAD_INCOMPLETE");
  }

  @Test
  void exactlyThePlannedPartsAreComplete() {
    PartPlan plan = PartPlan.forSize(12 * MIB);
    List<StoredPart> stored = new ArrayList<>();
    for (int n = 1; n <= plan.partCount(); n++) {
      stored.add(new StoredPart(n, plan.expectedSize(n), "e" + n));
    }
    assertThat(plan.check(stored).complete()).isTrue();
  }
}
