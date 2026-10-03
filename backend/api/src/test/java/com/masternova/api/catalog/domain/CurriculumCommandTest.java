package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.catalog.domain.CurriculumCommand.AddLecture;
import com.masternova.api.catalog.domain.CurriculumCommand.AddSection;
import com.masternova.api.catalog.domain.CurriculumCommand.MoveLecture;
import com.masternova.api.catalog.domain.CurriculumCommand.RemoveLecture;
import com.masternova.api.catalog.domain.CurriculumCommand.RemoveSection;
import com.masternova.api.catalog.domain.CurriculumCommand.RenameSection;
import com.masternova.api.catalog.domain.CurriculumCommand.ReorderSections;
import com.masternova.api.catalog.domain.CurriculumCommand.RestoreSection;
import com.masternova.api.catalog.domain.CurriculumCommand.UpdateLecture;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The Command pattern: every edit round-trips through its own inverse. No Spring, no DB. */
class CurriculumCommandTest {

  static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
  static final UUID ASSET = UUID.randomUUID();

  /** Intro [Welcome*, Setup] · Core [Pods, Services, Notes] — * = preview */
  static Course course() {
    return aCourse()
        .withSection(
            "Intro", aLecture("Welcome").preview().seconds(90).asset(ASSET), aLecture("Setup"))
        .withSection(
            "Core",
            aLecture("Pods").seconds(600),
            aLecture("Services"),
            aLecture("Notes").article())
        .build();
  }

  /** Everything a learner or an editor could observe: ids, titles, order, flags, rollups. */
  static String view(Course c) {
    StringBuilder out = new StringBuilder();
    for (Section s : c.sections()) {
      out.append(s.position()).append(':').append(s.id()).append(':').append(s.title()).append('[');
      for (Lecture l : s.lectures()) {
        out.append(l.position())
            .append(':')
            .append(l.id())
            .append(':')
            .append(l.title())
            .append(l.isPreview() ? "*" : "")
            .append(':')
            .append(l.duration().seconds())
            .append(':')
            .append(l.assetId().orElse(null))
            .append(' ');
      }
      out.append("] ");
    }
    return out.append("count=")
        .append(c.lectureCount())
        .append(" seconds=")
        .append(c.totalDuration().seconds())
        .toString();
  }

  static UUID section(Course c, int i) {
    return c.sections().get(i).id();
  }

  static UUID lecture(Course c, int s, int l) {
    return c.sections().get(s).lectures().get(l).id();
  }

  /** One command of every client-sendable kind, built against the course it will edit. */
  static Stream<Arguments> everyKind() {
    List<Function<Course, CurriculumCommand>> commands =
        List.of(
            c -> new AddSection(null, "Bonus").withIds(),
            c -> new RenameSection(section(c, 0), "Getting started"),
            c -> new ReorderSections(List.of(section(c, 1), section(c, 0))),
            c -> new RemoveSection(section(c, 1)),
            c ->
                new AddLecture(null, section(c, 0), "Extra", LectureKind.ARTICLE, true, 30)
                    .withIds(),
            c -> new UpdateLecture(lecture(c, 0, 1), "Install everything", true),
            c -> new MoveLecture(lecture(c, 1, 0), section(c, 0), 0), // across sections, to the top
            c -> new MoveLecture(lecture(c, 1, 0), section(c, 1), 2), // within a section
            c -> new RemoveLecture(lecture(c, 0, 0))); // the preview with a media asset
    return commands.stream().map(Arguments::of);
  }

  /**
   * ⭐ THE Command property: apply, then apply the inverse it returned → exactly the original
   * curriculum — same ids, same positions, same flags, same rollups.
   */
  @ParameterizedTest
  @MethodSource("everyKind")
  void everyCommandRoundTripsThroughItsInverse(Function<Course, CurriculumCommand> make) {
    Course course = course();
    String before = view(course);
    CurriculumCommand command = make.apply(course);

    CurriculumCommand inverse = course.apply(command, NOW);
    assertThat(view(course)).as("the command changed something").isNotEqualTo(before);
    course.apply(inverse, NOW);

    assertThat(view(course)).isEqualTo(before);
  }

  @Test
  void removingASectionCapturesAMementoThatRestoresTheSameIdsAndMedia() {
    Course course = course();
    UUID core = section(course, 1);
    UUID pods = lecture(course, 1, 0);

    CurriculumCommand inverse = course.apply(new RemoveSection(core), NOW);

    assertThat(inverse)
        .isInstanceOfSatisfying(
            RestoreSection.class,
            restore -> {
              assertThat(restore.position()).isEqualTo(1);
              assertThat(restore.snapshot().id()).isEqualTo(core);
              assertThat(restore.snapshot().lectures())
                  .extracting(LectureSnapshot::id)
                  .contains(pods);
            });
    assertThat(course.lectureCount()).isEqualTo(2); // rollups follow the edit
    assertThat(inverse.clientAllowed()).isFalse(); // only the server may restore snapshots
  }

  @Test
  void movingALectureKeepsPositionsDenseInBothSections() {
    Course course = course();
    UUID pods = lecture(course, 1, 0);

    course.apply(new MoveLecture(pods, section(course, 0), 1), NOW);

    assertThat(course.sections().get(0).lectures())
        .extracting(Lecture::title, Lecture::position)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Welcome", 0),
            org.assertj.core.groups.Tuple.tuple("Pods", 1),
            org.assertj.core.groups.Tuple.tuple("Setup", 2));
    assertThat(course.sections().get(1).lectures())
        .extracting(Lecture::position)
        .containsExactly(0, 1);
  }

  @Test
  void aReorderMustBeAPermutation() {
    Course course = course();

    assertThatThrownBy(() -> course.apply(new ReorderSections(List.of(section(course, 0))), NOW))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
            () ->
                course.apply(
                    new ReorderSections(List.of(section(course, 0), UUID.randomUUID())), NOW))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void aCommandNamingAnotherCoursesNodeIsNotFound() {
    Course course = course();
    UUID foreign = section(course(), 0);

    assertThatThrownBy(() -> course.apply(new RenameSection(foreign, "Mine now"), NOW))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void addingTheSameIdTwiceIsAConflict() {
    Course course = course();
    UUID id = UUID.randomUUID();
    course.apply(new AddSection(id, "Once"), NOW);

    assertThatThrownBy(() -> course.apply(new AddSection(id, "Twice"), NOW))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void anArchivedCourseRefusesEveryEdit() {
    Course course = course();
    course.transition(CourseAction.ARCHIVE, NOW);

    assertThatThrownBy(() -> course.apply(new AddSection(null, "Late").withIds(), NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.code()).isEqualTo("COURSE_ARCHIVED"));
  }

  @Test
  void anEditTouchesTheRootSoTheVersionWillMove() {
    Course course = course();

    course.apply(new RenameSection(section(course, 0), "Start"), NOW.plusSeconds(9));

    assertThat(course.updatedAt()).isEqualTo(NOW.plusSeconds(9));
  }

  @Test
  void badInputFailsWhileBuildingTheCommand() {
    assertThatThrownBy(() -> new AddSection(null, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AddLecture(null, UUID.randomUUID(), "x", null, false, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new AddSection(null, "x").withIds())
        .isInstanceOfSatisfying(AddSection.class, a -> assertThat(a.sectionId()).isNotNull());
  }
}
