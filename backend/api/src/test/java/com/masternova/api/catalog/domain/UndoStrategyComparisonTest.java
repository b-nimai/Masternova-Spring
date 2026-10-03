package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.catalog.domain.CurriculumCommand.MoveLecture;
import com.masternova.api.catalog.domain.CurriculumCommand.RemoveLecture;
import com.masternova.api.catalog.domain.CurriculumCommand.RemoveSection;
import com.masternova.api.catalog.domain.CurriculumCommand.RenameSection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 6.5's "build both, keep one": what ONE history entry costs with each undo strategy, measured on a
 * realistic 10-section × 5-lecture course (ADR-0011).
 *
 * <ul>
 *   <li>INVERSE COMMANDS (shipped): store the command + the inverse it returned.
 *   <li>SNAPSHOT STACK (the Memento-only design): store the whole curriculum before every edit.
 * </ul>
 */
class UndoStrategyComparisonTest {

  final JsonMapper json = JsonMapper.builder().build();
  static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

  private int bytes(Object value) {
    return json.writerFor(Object.class)
        .writeValueAsString(value)
        .getBytes(StandardCharsets.UTF_8)
        .length;
  }

  private int commandEntryBytes(Course course, CurriculumCommand command) {
    CurriculumCommand inverse = course.apply(command, NOW);
    return json.writerFor(CurriculumCommand.class).writeValueAsString(command).length()
        + json.writerFor(CurriculumCommand.class).writeValueAsString(inverse).length();
  }

  /** The whole curriculum as Mementos — what a snapshot-per-edit design stores every time. */
  private int snapshotEntryBytes(Course course) {
    List<SectionSnapshot> snapshot = course.sections().stream().map(Section::snapshot).toList();
    return bytes(snapshot);
  }

  @Test
  void inverseCommandsCostAFractionOfAWholeSnapshotPerEdit() {
    Map<String, Function<Course, CurriculumCommand>> edits = new LinkedHashMap<>();
    edits.put("rename a section", c -> new RenameSection(c.sections().get(3).id(), "Networking"));
    edits.put(
        "move a lecture",
        c ->
            new MoveLecture(
                c.sections().get(2).lectures().get(0).id(), c.sections().get(5).id(), 1));
    edits.put(
        "remove a lecture", c -> new RemoveLecture(c.sections().get(1).lectures().get(2).id()));
    edits.put("remove a section (5 lectures)", c -> new RemoveSection(c.sections().get(7).id()));

    System.out.printf("%-32s %10s %10s%n", "edit", "commands", "snapshot");
    for (var edit : edits.entrySet()) {
      Course course = aCourse().withCurriculum(10, 5).build();
      int snapshot = snapshotEntryBytes(course);
      int commands = commandEntryBytes(course, edit.getValue().apply(course));
      System.out.printf("%-32s %9dB %9dB%n", edit.getKey(), commands, snapshot);

      // ⭐ even the biggest inverse (a whole removed section) is far smaller than the curriculum
      assertThat(commands).as(edit.getKey()).isLessThan(snapshot / 4);
    }
  }
}
