package com.masternova.api.learning.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.kernel.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Why commerce computes in {@code long} minor units (Money) and not in {@code BigDecimal} or {@code
 * double} — every trap here is one a discount calculation can fall into (commerce LLD §2, Phase
 * 9.2). Each test is a pitfall, then the Money way.
 */
class BigDecimalPitfallsLearningTest {

  @Test
  void doubleCantHoldATenth() {
    assertThat(0.1 + 0.2).isNotEqualTo(0.3); // 0.30000000000000004
    assertThat(new BigDecimal(0.1)).isNotEqualTo(new BigDecimal("0.1")); // ⭐ the double's error
    assertThat(new BigDecimal(0.1).toPlainString()).startsWith("0.1000000000000000055");
    assertThat(BigDecimal.valueOf(0.1)).isEqualTo(new BigDecimal("0.1")); // via Double.toString
  }

  @Test
  void equalsComparesTheScaleToo() {
    BigDecimal a = new BigDecimal("2.0");
    BigDecimal b = new BigDecimal("2.00");

    assertThat(a.equals(b)).isFalse(); // ⭐ 2.0 ≠ 2.00 to equals (and to a HashSet)
    assertThat(a.compareTo(b)).isZero(); // equal as numbers
    assertThat(a.stripTrailingZeros()).isEqualTo(b.stripTrailingZeros());
    // Money has one representation per amount: 200 paise is 200 paise
    assertThat(Money.parse("2.0", "INR")).isEqualTo(Money.parse("2.00", "INR"));
  }

  @Test
  void divisionNeedsARoundingMode() {
    BigDecimal ten = new BigDecimal("10");
    assertThatThrownBy(() -> ten.divide(new BigDecimal("3")))
        .isInstanceOf(ArithmeticException.class); // non-terminating decimal expansion
    assertThat(ten.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP))
        .isEqualByComparingTo("3.33");
    // ⭐ and 3.33 × 3 = 9.99: a paisa vanished. Splitting money is ALLOCATION, not division
    assertThat(Money.of(1000, "INR").allocate(3))
        .extracting(Money::amountMinor)
        .containsExactly(334L, 333L, 333L);
  }

  @Test
  void theDefaultRoundingIsBankers() {
    // HALF_EVEN (banker's) rounds 0.5 to the even neighbour; a shop's receipt expects HALF_UP
    assertThat(new BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN)).isEqualByComparingTo("2");
    assertThat(new BigDecimal("2.5").setScale(0, RoundingMode.HALF_UP)).isEqualByComparingTo("3");
    // Math.round on a double is HALF_UP but inherits the double's error
    assertThat(Math.round(1.005 * 100) / 100.0).isEqualTo(1.0); // 1.00499999… → 1.00, not 1.01
  }

  @Test
  void minorUnitsMakeAPercentageExact() {
    // 15% of ₹333.33: integer half-up in Money, no BigDecimal and no double anywhere
    Money price = Money.of(33_333, "INR");
    assertThat(price.minus(price.percentOff(15)))
        .isEqualTo(Money.of(5_000, "INR")); // 4999.95 → 5000
    // the same with BigDecimal needs the scale AND the mode chosen right, every time
    assertThat(
            new BigDecimal("333.33")
                .multiply(new BigDecimal("0.15"))
                .setScale(2, RoundingMode.HALF_UP))
        .isEqualByComparingTo("50.00");
  }
}
