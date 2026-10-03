package com.masternova.patterns.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandTest {

  static Outline outline() {
    return new Outline(List.of("Intro", "Core", "Wrap-up"));
  }

  @Test
  void everyEditRoundTripsThroughItsInverse() {
    for (Edit edit : List.of(new Edit.Add(1, "Bonus"), new Edit.Remove(0), new Edit.Rename(2, "End"))) {
      Outline o = outline();
      Edit inverse = edit.applyTo(o);
      inverse.applyTo(o);
      assertThat(o.sections()).as("%s", edit).isEqualTo(outline().sections());
    }
  }

  @Test
  void undoAndRedoWalkTheHistory() {
    Outline o = outline();
    History history = new History(o);
    history.apply(new Edit.Remove(1));
    history.apply(new Edit.Rename(0, "Start"));

    assertThat(o.sections()).containsExactly("Start", "Wrap-up");
    history.undo();
    history.undo();
    assertThat(o.sections()).containsExactly("Intro", "Core", "Wrap-up");
    history.redo();
    assertThat(o.sections()).containsExactly("Intro", "Wrap-up");
  }

  @Test
  void aNewEditAfterUndoDiscardsTheRedoBranch() {
    History history = new History(outline());
    history.apply(new Edit.Remove(0));
    history.undo();

    history.apply(new Edit.Rename(0, "Different"));

    assertThat(history.redo()).isFalse();
    assertThat(new History(outline()).undo()).isFalse(); // nothing to undo
  }
}
