package com.masternova.api.catalog.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One curriculum edit as a VALUE: the COMMAND pattern (docs/lld/catalog-authoring.md §6).
 *
 * <ul>
 *   <li>⭐ {@link #applyTo} performs the edit on the aggregate AND returns the command that undoes
 *       it. The inverse is computed at the only moment it can be: the inverse of "remove section 3"
 *       is "put back exactly this section with its lectures", which exists only before the delete.
 *   <li>⭐ A command is data: Jackson reads it from the request ({@code "kind": "MOVE_LECTURE"}) and
 *       writes it, with its inverse, into the edit history (6.5). One route ({@code POST
 *       …/curriculum}) instead of nine — and a new edit type is a new record here, not a new
 *       endpoint, service method and undo path.
 *   <li>Commands carry IDS (generated when the client omits them): a redo of "add section" must
 *       recreate the same id, or the redo of "add a lecture to it" would point nowhere.
 *   <li>Sealed: the set of edits is closed, so every {@code switch} over them is exhaustive.
 * </ul>
 *
 * <p>Pattern note: patterns/docs/04-command.md. API conventions §6.
 */
@DesignPattern(
    value = Pattern.COMMAND,
    role = "Command (applies itself, returns its inverse)",
    note = "patterns/docs/04-command.md")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CurriculumCommand.AddSection.class, name = "ADD_SECTION"),
  @JsonSubTypes.Type(value = CurriculumCommand.RenameSection.class, name = "RENAME_SECTION"),
  @JsonSubTypes.Type(value = CurriculumCommand.ReorderSections.class, name = "REORDER_SECTIONS"),
  @JsonSubTypes.Type(value = CurriculumCommand.RemoveSection.class, name = "REMOVE_SECTION"),
  @JsonSubTypes.Type(value = CurriculumCommand.RestoreSection.class, name = "RESTORE_SECTION"),
  @JsonSubTypes.Type(value = CurriculumCommand.AddLecture.class, name = "ADD_LECTURE"),
  @JsonSubTypes.Type(value = CurriculumCommand.UpdateLecture.class, name = "UPDATE_LECTURE"),
  @JsonSubTypes.Type(value = CurriculumCommand.MoveLecture.class, name = "MOVE_LECTURE"),
  @JsonSubTypes.Type(value = CurriculumCommand.RemoveLecture.class, name = "REMOVE_LECTURE"),
  @JsonSubTypes.Type(value = CurriculumCommand.RestoreLecture.class, name = "RESTORE_LECTURE")
})
public sealed interface CurriculumCommand {

  /** Performs the edit on {@code course} (through its root methods) and returns its inverse. */
  CurriculumCommand applyTo(Course course);

  /**
   * May a client send this kind? RESTORE_* only exist as inverses: a client-made snapshot could
   * attach another course's media asset ids.
   */
  default boolean clientAllowed() {
    return true;
  }

  /** The same command with any missing ids generated (so a redo recreates the same rows). */
  default CurriculumCommand withIds() {
    return this;
  }

  // ------------------------------------------------------------------ sections

  record AddSection(UUID sectionId, String title) implements CurriculumCommand {
    public AddSection {
      title = requireTitle(title);
    }

    @Override
    public CurriculumCommand withIds() {
      return sectionId != null ? this : new AddSection(UUID.randomUUID(), title);
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      course.addSection(Objects.requireNonNull(sectionId, "sectionId"), title);
      return new RemoveSection(sectionId);
    }
  }

  record RenameSection(UUID sectionId, String title) implements CurriculumCommand {
    public RenameSection {
      Objects.requireNonNull(sectionId, "sectionId");
      title = requireTitle(title);
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      String before = course.section(sectionId).title();
      course.renameSection(sectionId, title);
      return new RenameSection(sectionId, before);
    }
  }

  /** The WHOLE order, not "move X to index n": a total operation two tabs can't half-apply. */
  record ReorderSections(List<UUID> order) implements CurriculumCommand {
    public ReorderSections {
      order = List.copyOf(order);
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      List<UUID> before = course.sectionOrder();
      course.reorderSections(order);
      return new ReorderSections(before);
    }
  }

  record RemoveSection(UUID sectionId) implements CurriculumCommand {
    public RemoveSection {
      Objects.requireNonNull(sectionId, "sectionId");
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      int position = course.positionOf(sectionId);
      SectionSnapshot snapshot = course.removeSection(sectionId); // ⭐ captured before the delete
      return new RestoreSection(snapshot, position);
    }
  }

  /** Inverse only: puts a removed section back, same ids, same place. */
  record RestoreSection(SectionSnapshot snapshot, int position) implements CurriculumCommand {
    public RestoreSection {
      Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public boolean clientAllowed() {
      return false;
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      course.restoreSection(snapshot, position);
      return new RemoveSection(snapshot.id());
    }
  }

  // ------------------------------------------------------------------ lectures

  record AddLecture(
      UUID lectureId,
      UUID sectionId,
      String title,
      LectureKind lectureKind, // not "kind": that's the command's type property
      Boolean preview, // ⭐ boxed: optional in the JSON (Jackson 3 refuses null for primitives)
      Integer durationSeconds)
      implements CurriculumCommand {
    public AddLecture {
      Objects.requireNonNull(sectionId, "sectionId");
      title = requireTitle(title);
      lectureKind = lectureKind == null ? LectureKind.VIDEO : lectureKind;
      preview = preview != null && preview; // absent → false
      durationSeconds = durationSeconds == null ? 0 : durationSeconds;
      if (durationSeconds < 0) {
        throw new IllegalArgumentException("durationSeconds cannot be negative");
      }
    }

    @Override
    public CurriculumCommand withIds() {
      return lectureId != null
          ? this
          : new AddLecture(
              UUID.randomUUID(), sectionId, title, lectureKind, preview, durationSeconds);
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      course.addLecture(
          Objects.requireNonNull(lectureId, "lectureId"),
          sectionId,
          title,
          lectureKind,
          preview,
          LectureDuration.ofSeconds(durationSeconds));
      return new RemoveLecture(lectureId);
    }
  }

  record UpdateLecture(UUID lectureId, String title, boolean preview) implements CurriculumCommand {
    public UpdateLecture {
      Objects.requireNonNull(lectureId, "lectureId");
      title = requireTitle(title);
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      Lecture before = course.lecture(lectureId);
      UpdateLecture inverse = new UpdateLecture(lectureId, before.title(), before.isPreview());
      course.updateLecture(lectureId, title, preview);
      return inverse;
    }
  }

  record MoveLecture(UUID lectureId, UUID toSectionId, int toPosition)
      implements CurriculumCommand {
    public MoveLecture {
      Objects.requireNonNull(lectureId, "lectureId");
      Objects.requireNonNull(toSectionId, "toSectionId");
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      Course.Place from = course.placeOf(lectureId);
      course.moveLecture(lectureId, toSectionId, toPosition);
      return new MoveLecture(lectureId, from.sectionId(), from.position());
    }
  }

  record RemoveLecture(UUID lectureId) implements CurriculumCommand {
    public RemoveLecture {
      Objects.requireNonNull(lectureId, "lectureId");
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      Course.Place place = course.placeOf(lectureId);
      LectureSnapshot snapshot = course.removeLecture(lectureId);
      return new RestoreLecture(snapshot, place.sectionId(), place.position());
    }
  }

  /** Inverse only: puts a removed lecture back, same id, same place. */
  record RestoreLecture(LectureSnapshot snapshot, UUID sectionId, int position)
      implements CurriculumCommand {
    public RestoreLecture {
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(sectionId, "sectionId");
    }

    @Override
    public boolean clientAllowed() {
      return false;
    }

    @Override
    public CurriculumCommand applyTo(Course course) {
      course.restoreLecture(snapshot, sectionId, position);
      return new RemoveLecture(snapshot.id());
    }
  }

  // ------------------------------------------------------------------ validation

  /** Bad input fails while Jackson builds the record: a 400, never a half-applied edit. */
  private static String requireTitle(String title) {
    if (title == null || title.isBlank() || title.strip().length() > 120) {
      throw new IllegalArgumentException("a title needs 1–120 characters");
    }
    return title.strip();
  }
}
