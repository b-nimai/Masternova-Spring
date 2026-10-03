package com.masternova.patterns.command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The INVOKER with undo/redo. Each entry keeps the command AND its inverse: undo applies the
 * inverse, redo re-applies the command. In production this history is a table (course_edit), not
 * these in-memory deques: it must survive a second api replica and a deploy (6.5).
 */
public final class History {

  private record Entry(Edit command, Edit inverse) {}

  private final Outline outline;
  private final Deque<Entry> done = new ArrayDeque<>();
  private final Deque<Entry> undone = new ArrayDeque<>();

  public History(Outline outline) {
    this.outline = outline;
  }

  public void apply(Edit command) {
    done.push(new Entry(command, command.applyTo(outline)));
    undone.clear(); // ⭐ a new edit after an undo discards the redo branch, like every editor
  }

  public boolean undo() {
    if (done.isEmpty()) {
      return false;
    }
    Entry entry = done.pop();
    entry.inverse().applyTo(outline);
    undone.push(entry);
    return true;
  }

  public boolean redo() {
    if (undone.isEmpty()) {
      return false;
    }
    Entry entry = undone.pop();
    done.push(new Entry(entry.command(), entry.command().applyTo(outline)));
    return true;
  }
}
