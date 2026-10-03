package com.masternova.patterns.memento;

import java.util.ArrayList;
import java.util.List;

/**
 * The ORIGINATOR: a course draft whose state can be saved and restored. Only it can create and read
 * its mementos (their content is private to it), so the caretaker stores them without knowing
 * what's inside — the encapsulation the pattern is about. Pattern note: patterns/docs/05-memento.md.
 */
public final class Draft {

  private String title;
  private final List<String> sections = new ArrayList<>();

  public Draft(String title) {
    this.title = title;
  }

  /** ⭐ The MEMENTO: an opaque, immutable copy of the state. Nobody outside can read its fields. */
  public static final class Memento {
    private final String title;
    private final List<String> sections;

    private Memento(String title, List<String> sections) {
      this.title = title;
      this.sections = List.copyOf(sections); // a COPY: later edits mustn't change the saved state
    }
  }

  public Memento save() {
    return new Memento(title, sections);
  }

  public void restore(Memento memento) {
    title = memento.title;
    sections.clear();
    sections.addAll(memento.sections);
  }

  public void rename(String newTitle) {
    title = newTitle;
  }

  public void addSection(String section) {
    sections.add(section);
  }

  public void removeSection(String section) {
    sections.remove(section);
  }

  public String title() {
    return title;
  }

  public List<String> sections() {
    return List.copyOf(sections);
  }
}
