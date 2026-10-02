package com.masternova.patterns.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuilderTest {

  @Test
  void namedStepsAndDefaults() {
    CourseListing listing =
        CourseListing.builder("Kubernetes", 149900).subtitle("Hands-on").featured(true).build();

    assertThat(listing.title()).isEqualTo("Kubernetes");
    assertThat(listing.language()).isEqualTo("en"); // the default
    assertThat(listing.featured()).isTrue();
  }

  @Test
  void buildValidatesTheWholeObjectIncludingCrossFieldRules() {
    assertThatThrownBy(() -> CourseListing.builder(" ", 0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CourseListing.builder("K8s", -1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CourseListing.builder("K8s", 0).featured(true).build())
        .hasMessageContaining("featured");
  }

  @Test
  void theResultIsImmutableEvenIfTheInputListChanges() {
    List<String> tags = new ArrayList<>(List.of("devops"));
    CourseListing listing = CourseListing.builder("K8s", 1).tags(tags).build();

    tags.add("sneaky");

    assertThat(listing.tags()).containsExactly("devops");
    assertThatThrownBy(() -> listing.tags().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void toBuilderMakesTheSameButDifferentWithoutMutation() {
    CourseListing original = CourseListing.builder("K8s", 149900).language("hi").build();

    CourseListing english = original.toBuilder().language("en").build();

    assertThat(original.language()).isEqualTo("hi"); // untouched
    assertThat(english.language()).isEqualTo("en");
    assertThat(english.priceMinor()).isEqualTo(149900);
  }
}
