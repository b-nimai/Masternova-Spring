package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseAction.ARCHIVE;
import static com.masternova.api.catalog.domain.CourseAction.PUBLISH;
import static com.masternova.api.catalog.domain.CourseAction.SUBMIT;
import static com.masternova.api.catalog.domain.CourseAction.UNPUBLISH;
import static com.masternova.api.catalog.domain.CourseAction.WITHDRAW;
import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseStatus.ARCHIVED;
import static com.masternova.api.catalog.domain.CourseStatus.DRAFT;
import static com.masternova.api.catalog.domain.CourseStatus.IN_REVIEW;
import static com.masternova.api.catalog.domain.CourseStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.RuleViolationException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The State pattern, proven over EVERY (state, action) pair — no Spring, no database. */
class CourseStateTest {

  static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

  /** ⭐ The diagram as data: every legal edge. Anything not listed must be illegal. */
  static final Map<CourseStatus, Map<CourseAction, CourseStatus>> LEGAL =
      new EnumMap<>(CourseStatus.class);

  static {
    LEGAL.put(DRAFT, Map.of(SUBMIT, IN_REVIEW, ARCHIVE, ARCHIVED));
    LEGAL.put(IN_REVIEW, Map.of(PUBLISH, PUBLISHED, WITHDRAW, DRAFT, ARCHIVE, ARCHIVED));
    LEGAL.put(PUBLISHED, Map.of(UNPUBLISH, DRAFT, ARCHIVE, ARCHIVED));
    LEGAL.put(ARCHIVED, Map.of());
  }

  static Stream<Arguments> everyPair() {
    return Stream.of(CourseStatus.values())
        .flatMap(s -> Stream.of(CourseAction.values()).map(a -> Arguments.of(s, a)));
  }

  @ParameterizedTest(name = "{0} --{1}-->")
  @MethodSource("everyPair")
  void everyStateActionPairIsLegalOrIllegalExactlyAsDrawn(CourseStatus from, CourseAction action) {
    CourseState state = CourseState.of(from);
    CourseStatus expected = LEGAL.get(from).get(action);

    if (expected != null) {
      assertThat(state.on(action)).isEqualTo(expected);
    } else {
      assertThatThrownBy(() -> state.on(action))
          .isInstanceOfSatisfying(
              ConflictException.class,
              e -> {
                assertThat(e.code()).isEqualTo("ILLEGAL_TRANSITION");
                assertThat(e.details()).containsEntry("from", from.name());
              });
    }
  }

  /** ⭐ The edge that would make review optional must not exist. */
  @Test
  void aDraftCanNeverBePublishedDirectly() {
    assertThatThrownBy(() -> CourseState.of(DRAFT).on(PUBLISH))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void onlyArchivedRefusesEdits() {
    for (CourseStatus status : CourseStatus.values()) {
      assertThat(CourseState.of(status).acceptsEdits()).isEqualTo(status != ARCHIVED);
    }
  }

  @Test
  void submittingAnIncompleteCourseReportsEveryMissingRequirement() {
    Course empty = aCourse().description("Too short.").unpriced().build();

    assertThatThrownBy(() -> empty.transition(SUBMIT, NOW))
        .isInstanceOfSatisfying(
            RuleViolationException.class,
            e -> {
              assertThat(e.code()).isEqualTo("COURSE_NOT_READY");
              assertThat(e.details().get("problems"))
                  .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                  .extracting("code")
                  .contains("DESCRIPTION_TOO_SHORT", "PRICE_NOT_SET", "NO_SECTIONS");
            });
    assertThat(empty.status()).isEqualTo(DRAFT); // nothing moved
  }

  @Test
  void archivingNeedsNoGateAndIsTerminal() {
    Course draft = aCourse().build(); // incomplete — archiving an unfinished draft is fine

    draft.transition(ARCHIVE, NOW);

    assertThat(draft.status()).isEqualTo(ARCHIVED);
    assertThatThrownBy(() -> draft.transition(ARCHIVE, NOW)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(draft::requireEditable)
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.code()).isEqualTo("COURSE_ARCHIVED"));
  }

  @Test
  void aTransitionTouchesTheRootSoItsVersionWillMove() {
    Course course = aCourse().createdAt(NOW).submitted().build();

    course.transition(WITHDRAW, NOW.plusSeconds(5));

    assertThat(course.status()).isEqualTo(DRAFT);
    assertThat(course.updatedAt()).isEqualTo(NOW.plusSeconds(5));
  }
}
