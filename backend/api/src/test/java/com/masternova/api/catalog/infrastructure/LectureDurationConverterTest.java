package com.masternova.api.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.catalog.domain.LectureDuration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The converter and the value object, without Spring or a database. */
class LectureDurationConverterTest {

  private final LectureDurationConverter converter = new LectureDurationConverter();

  @Test
  void roundTripsThroughAnIntegerColumn() {
    assertThat(converter.convertToDatabaseColumn(LectureDuration.ofSeconds(245))).isEqualTo(245);
    assertThat(converter.convertToEntityAttribute(245)).isEqualTo(LectureDuration.ofSeconds(245));
  }

  @Test
  void passesNullsThroughForTheColumnConstraintToRefuse() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void aCorruptNegativeRowFailsLoudlyOnLoad() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute(-5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void durationsAddUpAndOverflowIsAnError() {
    assertThat(LectureDuration.total(List.of())).isEqualTo(LectureDuration.ZERO);
    assertThat(
            LectureDuration.total(
                List.of(LectureDuration.ofSeconds(60), LectureDuration.ofSeconds(5))))
        .isEqualTo(LectureDuration.ofSeconds(65));
    assertThat(LectureDuration.ofSeconds(1)).isLessThan(LectureDuration.ofSeconds(2));
    assertThatThrownBy(
            () -> LectureDuration.ofSeconds(Integer.MAX_VALUE).plus(LectureDuration.ofSeconds(1)))
        .isInstanceOf(ArithmeticException.class);
  }
}
