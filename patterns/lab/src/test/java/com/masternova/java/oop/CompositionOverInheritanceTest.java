package com.masternova.java.oop;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.oop.inheritance.CountingTagSet;
import com.masternova.java.oop.inheritance.InstrumentedTagSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositionOverInheritanceTest {

  @Test
  void inheritanceDoubleCountsBecauseOfTheParentsInternals() {
    InstrumentedTagSet tags = new InstrumentedTagSet();

    tags.addAll(List.of("java", "spring", "docker"));

    // ❌ 3 tags added, but HashSet.addAll calls add() for each → our override counts again.
    assertThat(tags.addCount()).isEqualTo(6);
  }

  @Test
  void compositionCountsCorrectly() {
    CountingTagSet tags = new CountingTagSet();

    tags.addAll(List.of("java", "spring", "docker"));
    tags.add("java"); // a duplicate still counts as an add attempt

    assertThat(tags.addCount()).isEqualTo(4);
    assertThat(tags.size()).isEqualTo(3);
  }

  @Test
  void theCompositeHandsOutOnlyACopy() {
    CountingTagSet tags = new CountingTagSet();
    tags.add("java");

    tags.view().stream().toList(); // reading is fine

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tags.view().add("hack"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
