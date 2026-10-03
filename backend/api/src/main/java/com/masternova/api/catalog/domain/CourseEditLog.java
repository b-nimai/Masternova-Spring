package com.masternova.api.catalog.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The curriculum's undo/redo history — the CARETAKER of the Command/Memento pair: it keeps each
 * edit and its inverse without knowing what's inside them. Stored in {@code course_edit}, so it
 * survives replicas and deploys (ADR-0011).
 *
 * <pre>
 *   seq:     1     2     3     4
 *   state:  done  done  undone undone      undo → applies 2's inverse;  redo → re-applies 3
 * </pre>
 *
 * Invariant: every undone edit comes after every done one, because a NEW edit discards the undone
 * branch ({@link #discardRedo}).
 */
@DesignPattern(
    value = Pattern.MEMENTO,
    role = "Caretaker (undo/redo history)",
    note = "patterns/docs/05-memento.md")
public interface CourseEditLog {

  /** One stored edit. */
  record Entry(UUID id, long seq, CurriculumCommand command, CurriculumCommand inverse) {}

  void record(
      UUID courseId,
      CurriculumCommand command,
      CurriculumCommand inverse,
      long versionAfter,
      UUID actorId,
      Instant now);

  /** The newest edit that is still done — what undo reverses. */
  Optional<Entry> lastDone(UUID courseId);

  /** The oldest undone edit — what redo re-applies. */
  Optional<Entry> firstUndone(UUID courseId);

  void markUndone(UUID editId, Instant now);

  /** Back on the done side, with the inverse the re-application produced. */
  void markRedone(UUID editId, CurriculumCommand freshInverse);

  /** A new edit after an undo: the redo branch is gone (like every editor). */
  void discardRedo(UUID courseId);

  boolean canUndo(UUID courseId);

  boolean canRedo(UUID courseId);
}
