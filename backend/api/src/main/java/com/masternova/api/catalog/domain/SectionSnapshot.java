package com.masternova.api.catalog.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import java.util.UUID;

/**
 * A section and all its lectures, captured the instant before it's removed: a MEMENTO.
 *
 * <p>⭐ The inverse of "remove section" is "put back exactly this" — information that exists only
 * BEFORE the delete runs. So the {@code RemoveSection} command captures this snapshot while
 * applying, and its inverse ({@code RestoreSection}) carries it. Originator: {@code Course};
 * caretaker: the {@code course_edit} history (6.5). Pattern note: patterns/docs/05-memento.md.
 */
@DesignPattern(value = Pattern.MEMENTO, role = "Memento", note = "patterns/docs/05-memento.md")
public record SectionSnapshot(UUID id, String title, List<LectureSnapshot> lectures) {

  public SectionSnapshot {
    lectures = List.copyOf(lectures);
  }
}
