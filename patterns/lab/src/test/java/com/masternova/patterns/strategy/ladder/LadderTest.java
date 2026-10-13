package com.masternova.patterns.strategy.ladder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LadderTest {

  @Test
  void theSharedRuleAppliesToEveryStrategy() {
    for (String name : new String[] {"standard", "fast"}) {
      Ladder ladder = Ladder.named(name);
      assertThat(ladder.heightsFor(1080)).allMatch(h -> h <= 1080);
      assertThat(ladder.heightsFor(201)).isNotEmpty(); // a tiny source still gets a rung
    }
  }

  @Test
  void strategiesDifferOnlyInWhatTheyChoose() {
    assertThat(Ladder.named("standard").heightsFor(720)).containsExactly(360, 720);
    assertThat(Ladder.named("fast").heightsFor(720)).containsExactly(240, 360);
    assertThat(Ladder.named("standard").heightsFor(201)).containsExactly(200);
  }
}
