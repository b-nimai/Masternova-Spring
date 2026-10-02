package com.masternova.patterns.prototype;

import java.util.ArrayList;
import java.util.List;

/** A MUTABLE section: this is what a shallow copy gets wrong. */
public final class Section {

  private String title;
  private final List<Lecture> lectures = new ArrayList<>();

  public Section(String title) {
    this.title = title;
  }

  /** ⭐ Copy constructor: a NEW list. The lectures are immutable records, so sharing THEM is safe. */
  public Section(Section source) {
    this(source.title);
    this.lectures.addAll(source.lectures);
  }

  public void add(Lecture lecture) {
    lectures.add(lecture);
  }

  public void rename(String title) {
    this.title = title;
  }

  public String title() {
    return title;
  }

  public List<Lecture> lectures() {
    return List.copyOf(lectures);
  }
}
