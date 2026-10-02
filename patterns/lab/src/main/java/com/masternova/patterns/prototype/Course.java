package com.masternova.patterns.prototype;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ✅ The PROTOTYPE done with copy constructors (what the real {@code Course.duplicateAsDraft} does):
 * the object that knows its structure copies itself, and EACH class copies what IT owns.
 *
 * <ul>
 *   <li>deep: new {@code List}, new {@code Section}s (mutable)
 *   <li>shared: immutable {@code Lecture} records and their asset ids (gigabytes, never changed)
 *   <li>reset: {@code published} — a copy has no history
 * </ul>
 */
public final class Course {

  private final String title;
  private final List<Section> sections = new ArrayList<>();
  private boolean published;

  public Course(String title) {
    this.title = Objects.requireNonNull(title);
  }

  /** ⭐ The copy constructor: private, so {@link #copy} is the one way in. */
  private Course(Course source, String title) {
    this(title);
    source.sections.forEach(s -> this.sections.add(new Section(s))); // ⭐ DEEP
    this.published = false; // ⭐ RESET
  }

  /** The prototype operation: "a new course like this one". */
  public Course copy(String newTitle) {
    return new Course(this, newTitle);
  }

  public void addSection(Section section) {
    sections.add(section);
  }

  public void publish() {
    published = true;
  }

  public boolean isPublished() {
    return published;
  }

  public String title() {
    return title;
  }

  public List<Section> sections() {
    return List.copyOf(sections);
  }
}
