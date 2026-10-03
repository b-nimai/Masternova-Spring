package com.masternova.api.catalog.domain;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The publish gate, rule by rule, with no Spring and no database. */
class PublishGateTest {

  static final String LONG_DESCRIPTION =
      "Pods, deployments, services and ingress — everything you need to run an app on Kubernetes.";

  /** A course that meets every requirement. Each failing example below breaks exactly one. */
  static CourseBuilder ready() {
    return aCourse()
        .description(LONG_DESCRIPTION)
        .priced(149900)
        .withSection("Intro", aLecture("Welcome").preview(), aLecture("Setup"))
        .withSection("Core", aLecture("Pods"));
  }

  /**
   * ⭐ One failing example per requirement code. The test below checks that this map covers every
   * code in {@link PublishGate#REQUIREMENTS}, so a rule added without a test fails the build.
   */
  static final Map<String, Supplier<Course>> BREAKS_ONLY =
      Map.of(
          "DESCRIPTION_TOO_SHORT", () -> ready().description("Too short.").build(),
          "PRICE_NOT_SET", () -> ready().unpriced().build(),
          "NO_SECTIONS", () -> aCourse().description(LONG_DESCRIPTION).build(),
          "EMPTY_SECTION", () -> ready().withSection("Empty").build(),
          "TOO_FEW_LECTURES",
              () ->
                  aCourse()
                      .description(LONG_DESCRIPTION)
                      .withSection("Intro", aLecture("Welcome").preview(), aLecture("Bye"))
                      .build(),
          "NO_PREVIEW",
              () ->
                  aCourse()
                      .description(LONG_DESCRIPTION)
                      .withSection("Intro", aLecture("A"), aLecture("B"), aLecture("C"))
                      .build());

  @Test
  void aCompleteCourseHasNoProblems() {
    assertThat(PublishGate.problems(ready().build())).isEmpty();
  }

  @Test
  void everyRequirementHasAFailingExample() {
    assertThat(BREAKS_ONLY.keySet())
        .containsExactlyInAnyOrderElementsOf(
            PublishGate.REQUIREMENTS.stream().map(PublishRequirement::code).toList());
  }

  static Iterable<String> codes() {
    return PublishGate.REQUIREMENTS.stream().map(PublishRequirement::code).toList();
  }

  @ParameterizedTest
  @MethodSource("codes")
  void eachFailingExampleBreaksExactlyItsOwnRule(String code) {
    Course course = BREAKS_ONLY.get(code).get();

    // NO_SECTIONS implies TOO_FEW_LECTURES and NO_PREVIEW too: an empty course fails all three
    assertThat(PublishGate.problems(course)).extracting(PublishCheck::code).contains(code);
    if (!code.equals("NO_SECTIONS")) {
      assertThat(PublishGate.problems(course)).extracting(PublishCheck::code).containsOnly(code);
    }
  }

  @Test
  void theChecklistShowsEveryRuleInOrderWithItsResult() {
    var checklist = PublishGate.evaluate(ready().unpriced().build());

    assertThat(checklist)
        .extracting(PublishCheck::code)
        .containsExactly(
            "DESCRIPTION_TOO_SHORT",
            "PRICE_NOT_SET",
            "NO_SECTIONS",
            "EMPTY_SECTION",
            "TOO_FEW_LECTURES",
            "NO_PREVIEW");
    assertThat(checklist)
        .filteredOn(c -> !c.satisfied())
        .extracting(PublishCheck::code)
        .containsExactly("PRICE_NOT_SET");
  }

  @Test
  void freeIsADecidedPrice() {
    assertThat(PublishGate.problems(ready().free().build())).isEmpty();
  }
}
