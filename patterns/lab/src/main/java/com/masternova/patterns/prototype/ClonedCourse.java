package com.masternova.patterns.prototype;

import java.util.ArrayList;
import java.util.List;

/**
 * ❌ The {@code Cloneable} way, kept to show what goes wrong. {@code super.clone()} copies FIELDS:
 * the new object gets the SAME {@code sections} list, holding the SAME Section objects. Rename a
 * section in the "copy" and the original changes too.
 *
 * <p>The other {@code clone()} problems: {@code Cloneable} has no {@code clone} method (it's a
 * marker; {@code Object.clone} is protected), it bypasses constructors (so invariants), it forces a
 * checked {@code CloneNotSupportedException} and a cast, and it can't assign {@code final} fields.
 * Effective Java item 13: prefer a copy constructor or a copy factory.
 */
public class ClonedCourse implements Cloneable {

  private String title;
  private List<Section> sections = new ArrayList<>(); // not final: clone() would need to reassign

  public ClonedCourse(String title) {
    this.title = title;
  }

  public void addSection(Section section) {
    sections.add(section);
  }

  @Override
  public ClonedCourse clone() {
    try {
      return (ClonedCourse) super.clone(); // ⭐ SHALLOW: sections is the very same list
    } catch (CloneNotSupportedException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public String title() {
    return title;
  }

  public List<Section> sections() {
    return sections;
  }
}
