package com.masternova.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void isAValueComparedByAmountAndCurrency() {
    assertThat(Money.of(149900, "INR")).isEqualTo(new Money(149900, Currency.getInstance("INR")));
    assertThat(Money.of(149900, "INR")).isNotEqualTo(Money.of(149900, "USD"));
    assertThat(Money.of(149900, "INR")).hasSameHashCodeAs(Money.of(149900, "INR"));
  }

  @Test
  void anInvalidMoneyCannotBeBuilt() {
    assertThatThrownBy(() -> Money.of(-1, "INR")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Money(0, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Money.of(1, "RUPEES")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesMajorUnitsExactly() {
    assertThat(Money.parse("1499.5", "INR")).isEqualTo(Money.of(149950, "INR"));
    assertThat(Money.parse("500", "JPY")).isEqualTo(Money.of(500, "JPY")); // no minor unit
    assertThatThrownBy(() -> Money.parse("1.005", "INR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("more decimals");
  }

  @Test
  void arithmeticReturnsNewValuesAndRefusesMixedCurrencies() {
    Money price = Money.of(99900, "INR");

    assertThat(price.plus(Money.of(100, "INR"))).isEqualTo(Money.of(100000, "INR"));
    assertThat(price.minus(Money.of(900, "INR"))).isEqualTo(Money.of(99000, "INR"));
    assertThat(price.times(3)).isEqualTo(Money.of(299700, "INR"));
    assertThat(price).isEqualTo(Money.of(99900, "INR")); // unchanged: immutable

    assertThatThrownBy(() -> price.plus(Money.of(1, "USD")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency mismatch");
    assertThatThrownBy(() -> Money.of(1, "INR").minus(Money.of(2, "INR")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "INR").plus(Money.of(1, "INR")))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void discountsRoundHalfUpAndAllocationsAddUpExactly() {
    assertThat(Money.of(999, "INR").percentOff(15)).isEqualTo(Money.of(849, "INR"));
    assertThat(Money.of(1000, "INR").allocate(3))
        .containsExactly(Money.of(334, "INR"), Money.of(333, "INR"), Money.of(333, "INR"));
    assertThatThrownBy(() -> Money.of(1, "INR").percentOff(101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of(1, "INR").allocate(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ordersAndDisplays() {
    assertThat(Money.of(100, "INR")).isLessThan(Money.of(200, "INR"));
    assertThat(Money.of(149900, "INR").display()).isEqualTo("INR 1499.00");
    assertThat(Money.zero("USD").isZero()).isTrue();
    assertThatThrownBy(() -> Money.of(1, "INR").compareTo(Money.of(1, "USD")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of(1, "INR").times(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
