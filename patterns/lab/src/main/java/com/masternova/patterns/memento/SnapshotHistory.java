package com.masternova.patterns.memento;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * The CARETAKER of a snapshot-stack undo: save the WHOLE state before every edit; undo restores the
 * previous one. Simple and always correct — and the design ADR-0011 measured and did NOT ship for
 * the curriculum: every entry is the whole document (7.5 KB per edit for a 10×5 course vs ~0.2–0.9
 * KB for a command and its inverse).
 */
public final class SnapshotHistory {

  private final Draft draft;
  private final Deque<Draft.Memento> undo = new ArrayDeque<>();
  private final Deque<Draft.Memento> redo = new ArrayDeque<>();

  public SnapshotHistory(Draft draft) {
    this.draft = draft;
  }

  public void edit(Consumer<Draft> change) {
    undo.push(draft.save()); // ⭐ the whole state, before the change
    change.accept(draft);
    redo.clear();
  }

  public boolean undo() {
    if (undo.isEmpty()) {
      return false;
    }
    redo.push(draft.save());
    draft.restore(undo.pop());
    return true;
  }

  public boolean redo() {
    if (redo.isEmpty()) {
      return false;
    }
    undo.push(draft.save());
    draft.restore(redo.pop());
    return true;
  }

  public int stored() {
    return undo.size() + redo.size();
  }
}
