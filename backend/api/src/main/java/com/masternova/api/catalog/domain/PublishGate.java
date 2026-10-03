package com.masternova.api.catalog.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;

/**
 * Whether a course is complete enough to go to review / to the catalog: a SPECIFICATION that
 * EXPLAINS itself. A plain specification answers yes/no; this one is the AND of named requirements
 * and reports WHICH ones failed, by code, so one list produces both the 422 problem and the
 * wizard's per-step checklist (docs/lld/catalog-authoring.md §3).
 *
 * <p>⭐ Pure: no Spring, no database — every rule is unit-tested alone. Adding a rule is adding an
 * entry to {@link #REQUIREMENTS}; nothing else changes (the state machine just asks {@link
 * #problems}).
 */
@DesignPattern(
    value = Pattern.SPECIFICATION,
    role = "Composite of coded requirements (explainable)",
    note = "patterns/docs/08-specification.md")
public final class PublishGate {

  static final int MIN_DESCRIPTION_LENGTH = 50;
  static final int MIN_LECTURES = 3;

  /** The rules, in the order the wizard's checklist shows them. */
  public static final List<PublishRequirement> REQUIREMENTS =
      List.of(
          new PublishRequirement(
              "DESCRIPTION_TOO_SHORT",
              "Describe the course in at least " + MIN_DESCRIPTION_LENGTH + " characters.",
              c -> c.description().length() >= MIN_DESCRIPTION_LENGTH),
          new PublishRequirement(
              "PRICE_NOT_SET",
              "Confirm a price (free is a price too).",
              c -> c.priceSetAt().isPresent()),
          new PublishRequirement(
              "NO_SECTIONS", "Add at least one section.", c -> !c.sections().isEmpty()),
          new PublishRequirement(
              "EMPTY_SECTION",
              "Every section needs at least one lecture.",
              c -> c.sections().stream().noneMatch(s -> s.lectures().isEmpty())),
          new PublishRequirement(
              "TOO_FEW_LECTURES",
              "Add at least " + MIN_LECTURES + " lectures.",
              c -> c.lectureCount() >= MIN_LECTURES),
          new PublishRequirement(
              "NO_PREVIEW",
              "Mark at least one lecture as a free preview.",
              c ->
                  c.sections().stream()
                      .flatMap(s -> s.lectures().stream())
                      .anyMatch(Lecture::isPreview)));

  // Phase 7 adds MEDIA_NOT_READY: every VIDEO lecture has a transcoded asset.

  private PublishGate() {}

  /** Every requirement with its result — the checklist. */
  public static List<PublishCheck> evaluate(Course course) {
    return REQUIREMENTS.stream()
        .map(r -> new PublishCheck(r.code(), r.message(), r.isSatisfiedBy(course)))
        .toList();
  }

  /** Only the failures — empty means the course may move toward publication. */
  public static List<PublishCheck> problems(Course course) {
    return evaluate(course).stream().filter(check -> !check.satisfied()).toList();
  }
}
