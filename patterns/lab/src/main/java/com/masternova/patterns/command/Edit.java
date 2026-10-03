package com.masternova.patterns.command;

/**
 * The COMMAND: an edit as a value that applies itself and RETURNS ITS INVERSE — the shape the real
 * {@code CurriculumCommand} has. The inverse is computed while applying, because only then is the
 * information it needs (the removed title, the old name) still there. Pattern note:
 * patterns/docs/04-command.md.
 */
public sealed interface Edit {

  Edit applyTo(Outline outline);

  record Add(int index, String title) implements Edit {
    public Edit applyTo(Outline o) {
      o.insert(index, title);
      return new Remove(index);
    }
  }

  record Remove(int index) implements Edit {
    public Edit applyTo(Outline o) {
      String removed = o.removeAt(index); // ⭐ captured now; gone after this line
      return new Add(index, removed);
    }
  }

  record Rename(int index, String title) implements Edit {
    public Edit applyTo(Outline o) {
      String before = o.titleAt(index);
      o.rename(index, title);
      return new Rename(index, before);
    }
  }
}
