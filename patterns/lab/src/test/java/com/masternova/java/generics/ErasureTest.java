package com.masternova.java.generics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErasureTest {

  @Test
  void typeArgumentsDoNotExistAtRuntime() {
    List<String> strings = new ArrayList<>();
    List<Integer> numbers = new ArrayList<>();

    // ⭐ ERASURE: both are just ArrayList at runtime. That's why `x instanceof List<String>`,
    //    `new T()` and `new T[10]` don't compile — the JVM has no T to work with.
    assertThat(strings.getClass()).isSameAs(numbers.getClass());
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void rawTypesDeferTheErrorToAFarAwayLine() {
    List<String> titles = new ArrayList<>();
    List raw = titles; //            ⚠️ a RAW type: generics switched off, only a warning
    raw.add(42); //                  compiles and RUNS — "heap pollution"

    // The failure appears later, wherever a String is read — far from the real bug.
    assertThatThrownBy(() -> {
          String first = titles.get(0); // the compiler inserted a hidden (String) cast here
          first.length();
        })
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  void typeTokenCarriesTheTypeIntoRuntime() {
    TypedSettings settings = new TypedSettings();
    settings.put(Integer.class, 30);
    settings.put(String.class, "INR");

    Integer retries = settings.get(Integer.class); // no cast needed
    String currency = settings.get(String.class);

    assertThat(retries).isEqualTo(30);
    assertThat(currency).isEqualTo("INR");
    assertThat(settings.get(Long.class)).isNull();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void typeTokenRejectsAWrongValueEvenThroughARawCall() {
    TypedSettings settings = new TypedSettings();
    Class raw = Integer.class;

    assertThatThrownBy(() -> settings.put(raw, "not a number"))
        .isInstanceOf(ClassCastException.class);
  }
}
