package com.masternova.patterns.command;

import java.util.ArrayList;
import java.util.List;

/** The RECEIVER: an ordered list of section titles that commands edit. */
public final class Outline {

  private final List<String> sections = new ArrayList<>();

  public Outline(List<String> initial) {
    sections.addAll(initial);
  }

  void insert(int index, String title) {
    sections.add(index, title);
  }

  String removeAt(int index) {
    return sections.remove(index);
  }

  void rename(int index, String title) {
    sections.set(index, title);
  }

  String titleAt(int index) {
    return sections.get(index);
  }

  public List<String> sections() {
    return List.copyOf(sections);
  }
}
