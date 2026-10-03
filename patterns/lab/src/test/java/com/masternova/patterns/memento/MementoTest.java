package com.masternova.patterns.memento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MementoTest {

  @Test
  void aMementoIsACopyLaterEditsCantChange() {
    Draft draft = new Draft("K8s");
    draft.addSection("Intro");
    Draft.Memento saved = draft.save();

    draft.addSection("Core");
    draft.rename("Kubernetes");
    draft.restore(saved);

    assertThat(draft.title()).isEqualTo("K8s");
    assertThat(draft.sections()).containsExactly("Intro");
  }

  @Test
  void aSnapshotStackUndoesAndRedoesAnyEdit() {
    Draft draft = new Draft("K8s");
    SnapshotHistory history = new SnapshotHistory(draft);
    history.edit(d -> d.addSection("Intro"));
    history.edit(d -> d.addSection("Core"));
    history.edit(d -> d.removeSection("Intro"));

    history.undo();
    history.undo();
    assertThat(draft.sections()).containsExactly("Intro");
    history.redo();
    assertThat(draft.sections()).containsExactly("Intro", "Core");
  }

  @Test
  void itsCostIsAWholeSnapshotPerEdit() {
    Draft draft = new Draft("K8s");
    SnapshotHistory history = new SnapshotHistory(draft);
    for (int i = 0; i < 50; i++) {
      int n = i;
      history.edit(d -> d.addSection("Section " + n));
    }
    // 50 edits → 50 full copies, the 50th holding 49 sections: O(edits × size). ADR-0011.
    assertThat(history.stored()).isEqualTo(50);
  }
}
