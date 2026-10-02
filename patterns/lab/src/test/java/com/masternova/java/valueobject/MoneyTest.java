package com.masternova.java.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The specification for {@link Money}. Read the test names top to bottom — they ARE the rules. */
class MoneyTest {

  private static final Currency INR = Currency.getInstance("INR");

  private static Money inr(long paise) {
    return Money.of(paise, "INR");
  }

  @Nested
  class Creation {

    @Test
    void rejectsANullCurrency() {
      assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsANegativeAmount() {
      assertThatThrownBy(() -> new Money(-1, INR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofLooksUpTheCurrencyByCode() {
      Money price = Money.of(149_900, "INR");

      assertThat(price.amountMinor()).isEqualTo(149_900);
      assertThat(price.currency()).isEqualTo(INR);
    }

    @Test
    void ofRejectsAnUnknownCurrencyCode() {
      assertThatThrownBy(() -> Money.of(1, "XYZ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroIsZero() {
      assertThat(Money.zero("INR").isZero()).isTrue();
      assertThat(inr(1).isZero()).isFalse();
    }

    @Test
    void parsesMajorUnitsExactly() {
      assertThat(Money.parse("1499.00", "INR")).isEqualTo(inr(149_900));
      assertThat(Money.parse("1499.5", "INR")).isEqualTo(inr(149_950));
      assertThat(Money.parse("1499", "INR")).isEqualTo(inr(149_900));
      assertThat(Money.parse("0.10", "INR")).isEqualTo(inr(10));
      assertThat(Money.parse("500", "JPY")).isEqualTo(Money.of(500, "JPY"));
    }

    @Test
    void parseRejectsWhatIsNotAValidAmount() {
      assertThatThrownBy(() -> Money.parse("1.005", "INR")) // half a paisa
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> Money.parse("1.5", "JPY")) // yen has no minor unit
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> Money.parse("-1", "INR"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> Money.parse("abc", "INR"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ValueSemantics {

    @Test
    void twoMoniesWithTheSameValueAreEqualEvenThoughTheyAreDifferentObjects() {
      Money a = inr(100);
      Money b = inr(100);

      assertThat(a).isNotSameAs(b); // two objects…
      assertThat(a).isEqualTo(b); // …one value
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void sameAmountInADifferentCurrencyIsNotEqual() {
      assertThat(inr(100)).isNotEqualTo(Money.of(100, "USD"));
    }

    @Test
    void equalValuesCollapseInASet() {
      assertThat(new HashSet<>(List.of(inr(100), inr(100), inr(200)))).hasSize(2);
    }

    @Test
    void operationsReturnNewInstancesAndNeverChangeTheOriginal() {
      Money price = inr(1_000);

      Money total = price.plus(inr(500));

      assertThat(total).isEqualTo(inr(1_500));
      assertThat(price).isEqualTo(inr(1_000));
    }
  }

  @Nested
  class Arithmetic {

    @Test
    void plusAndMinus() {
      assertThat(inr(1_000).plus(inr(250))).isEqualTo(inr(1_250));
      assertThat(inr(1_000).minus(inr(250))).isEqualTo(inr(750));
      assertThat(inr(1_000).minus(inr(1_000)).isZero()).isTrue();
    }

    @Test
    void minusRefusesToGoBelowZero() {
      assertThatThrownBy(() -> inr(100).minus(inr(101)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mixingCurrenciesIsAnError() {
      Money dollars = Money.of(100, "USD");

      assertThatThrownBy(() -> inr(100).plus(dollars))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> inr(100).minus(dollars))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timesMultipliesByAQuantity() {
      assertThat(inr(49_900).times(3)).isEqualTo(inr(149_700));
      assertThat(inr(49_900).times(0).isZero()).isTrue();
      assertThatThrownBy(() -> inr(100).times(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowFailsLoudlyInsteadOfWrappingAround() {
      Money huge = inr(Long.MAX_VALUE / 2 + 1);

      assertThatThrownBy(() -> huge.times(2)).isInstanceOf(ArithmeticException.class);
      assertThatThrownBy(() -> huge.plus(huge)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void percentOffRoundsTheDiscountHalfUp() {
      assertThat(inr(999).percentOff(15)).isEqualTo(inr(849)); // discount 149.85 -> 150
      assertThat(inr(999).percentOff(10)).isEqualTo(inr(899)); // discount 99.9  -> 100
      assertThat(inr(1_000).percentOff(0)).isEqualTo(inr(1_000));
      assertThat(inr(1_000).percentOff(100).isZero()).isTrue();
    }

    @Test
    void percentOffOnlyAcceptsZeroToHundred() {
      assertThatThrownBy(() -> inr(100).percentOff(101))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> inr(100).percentOff(-5))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Allocation {

    @Test
    void splitsWithoutLosingAPaisa() {
      List<Money> shares = inr(1_000).allocate(3);

      assertThat(shares).containsExactly(inr(334), inr(333), inr(333));
      assertThat(shares.stream().reduce(Money.zero("INR"), Money::plus)).isEqualTo(inr(1_000));
    }

    @Test
    void smallAmountsStillAddUp() {
      assertThat(inr(2).allocate(3)).containsExactly(inr(1), inr(1), inr(0));
    }

    @Test
    void atLeastOnePart() {
      assertThatThrownBy(() -> inr(100).allocate(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReturnedListCannotBeModified() {
      List<Money> shares = inr(1_000).allocate(2);

      assertThatThrownBy(() -> shares.add(inr(1)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class OrderingAndDisplay {

    @Test
    void sortsByAmount() {
      assertThat(List.of(inr(300), inr(100), inr(200)).stream().sorted().toList())
          .containsExactly(inr(100), inr(200), inr(300));
    }

    @Test
    void comparingDifferentCurrenciesIsAnError() {
      assertThatThrownBy(() -> inr(100).compareTo(Money.of(100, "USD")))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayUsesTheCurrencysOwnDecimalPlaces() {
      assertThat(inr(149_900).display()).isEqualTo("INR 1499.00");
      assertThat(inr(5).display()).isEqualTo("INR 0.05");
      assertThat(Money.of(500, "JPY").display()).isEqualTo("JPY 500");
      assertThat(Money.zero("USD").display()).isEqualTo("USD 0.00");
    }
  }
}
