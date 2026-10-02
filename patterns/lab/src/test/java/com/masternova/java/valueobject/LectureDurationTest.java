package com.masternova.java.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The specification for {@link LectureDuration}. */
class LectureDurationTest {

  @Test
  void rejectsNegativeSeconds() {
    assertThatThrownBy(() -> new LectureDuration(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void factories() {
    assertThat(LectureDuration.ofSeconds(245).seconds()).isEqualTo(245);
    assertThat(LectureDuration.ofMinutes(90).seconds()).isEqualTo(5_400);
    assertThatThrownBy(() -> LectureDuration.ofMinutes(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LectureDuration.ofMinutes(Integer.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void parsesMinutesAndHours() {
    assertThat(LectureDuration.parse("4:05")).isEqualTo(LectureDuration.ofSeconds(245));
    assertThat(LectureDuration.parse("1:02:03")).isEqualTo(LectureDuration.ofSeconds(3_723));
    assertThat(LectureDuration.parse("0:00")).isEqualTo(LectureDuration.ofSeconds(0));
    assertThat(LectureDuration.parse("75:00")).isEqualTo(LectureDuration.ofSeconds(4_500));
  }

  @ParameterizedTest(name = "rejects \"{0}\"")
  @ValueSource(strings = {"", "abc", "4", "4:5", "1:60", "1:61:00", "-1:00", "1:02:03:04", " 4:05"})
  void parseRejectsInvalidText(String text) {
    assertThatThrownBy(() -> LectureDuration.parse(text))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseRejectsNull() {
    assertThatThrownBy(() -> LectureDuration.parse(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void formatsWithHoursOnlyWhenNeeded() {
    assertThat(LectureDuration.ofSeconds(245).format()).isEqualTo("4:05");
    assertThat(LectureDuration.ofSeconds(3_723).format()).isEqualTo("1:02:03");
    assertThat(LectureDuration.ofSeconds(4_500).format()).isEqualTo("1:15:00");
    assertThat(LectureDuration.ofSeconds(0).format()).isEqualTo("0:00");
  }

  @ParameterizedTest(name = "{0} s survives format -> parse")
  @ValueSource(ints = {0, 9, 59, 60, 245, 3_599, 3_600, 3_723, 86_399, 360_000})
  void formatAndParseAreInverses(int seconds) {
    LectureDuration original = LectureDuration.ofSeconds(seconds);

    assertThat(LectureDuration.parse(original.format())).isEqualTo(original);
  }

  @Test
  void addsUpASection() {
    List<LectureDuration> lectures =
        List.of(
            LectureDuration.parse("4:05"),
            LectureDuration.parse("10:00"),
            LectureDuration.parse("0:55"));

    assertThat(LectureDuration.total(lectures)).isEqualTo(LectureDuration.parse("15:00"));
    assertThat(LectureDuration.total(List.of())).isEqualTo(LectureDuration.ZERO);
    assertThat(LectureDuration.ofSeconds(30).plus(LectureDuration.ofSeconds(45)).seconds())
        .isEqualTo(75);
  }

  @Test
  void convertsToJavaTime() {
    assertThat(LectureDuration.ofSeconds(245).toJavaDuration()).isEqualTo(Duration.ofSeconds(245));
  }

  @Test
  void valueEqualityAndOrdering() {
    assertThat(LectureDuration.parse("4:05")).isEqualTo(LectureDuration.ofSeconds(245));
    assertThat(
            List.of(LectureDuration.ofSeconds(90), LectureDuration.ofSeconds(5)).stream()
                .sorted()
                .toList())
        .containsExactly(LectureDuration.ofSeconds(5), LectureDuration.ofSeconds(90));
  }
}
