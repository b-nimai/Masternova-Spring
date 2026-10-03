package com.masternova.api.catalog.domain;

import com.masternova.api.platform.ConflictException;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Map;

/**
 * The course lifecycle as the STATE pattern (docs/lld/catalog-authoring.md §3):
 *
 * <pre>
 *   DRAFT ──submit──► IN_REVIEW ──publish (ADMIN)──► PUBLISHED
 *     ▲                   │ withdraw                    │ unpublish
 *     └───────────────────┴─────────────────────────────┘
 *   any of the three ──archive──► ARCHIVED (terminal, read-only)
 * </pre>
 *
 * <ul>
 *   <li>⭐ Each event is a DEFAULT method that throws {@code ILLEGAL_TRANSITION}; each state
 *       OVERRIDES ONLY ITS LEGAL EVENTS. Java's default methods answer the classic objection to
 *       State ("4 states × 5 events = 20 methods, 17 of them throw"): here there are 5 overrides in
 *       total, and they ARE the diagram.
 *   <li>⭐ Sealed + records: the set of states is closed, so {@link #of} is an exhaustive switch —
 *       adding a status to {@code CourseStatus} won't compile until it has a state.
 *   <li>There is NO edge from DRAFT to PUBLISHED: review is not optional ({@code CourseStateTest}).
 *   <li>Who may (owner / ADMIN) is the application layer's job; WHETHER the course may is this.
 * </ul>
 */
@DesignPattern(value = Pattern.STATE, role = "State (sealed)", note = "patterns/docs/02-state.md")
public sealed interface CourseState {

  CourseStatus status();

  /** The next status for {@code action}, or {@code ILLEGAL_TRANSITION}. */
  default CourseStatus on(CourseAction action) {
    return switch (action) { // ⭐ exhaustive over the enum: a new action must be routed here
      case SUBMIT -> submit();
      case WITHDRAW -> withdraw();
      case PUBLISH -> publish();
      case UNPUBLISH -> unpublish();
      case ARCHIVE -> archive();
    };
  }

  default CourseStatus submit() {
    throw illegal(CourseAction.SUBMIT);
  }

  default CourseStatus withdraw() {
    throw illegal(CourseAction.WITHDRAW);
  }

  default CourseStatus publish() {
    throw illegal(CourseAction.PUBLISH);
  }

  default CourseStatus unpublish() {
    throw illegal(CourseAction.UNPUBLISH);
  }

  /** Every live state may be archived: archiving is this domain's delete. */
  default CourseStatus archive() {
    return CourseStatus.ARCHIVED;
  }

  /** May the content (details, pricing, curriculum) change in this state? */
  default boolean acceptsEdits() {
    return true;
  }

  private ConflictException illegal(CourseAction action) {
    return new ConflictException(
        "ILLEGAL_TRANSITION",
        "Can't " + action.name().toLowerCase() + " a " + status() + " course.",
        Map.of("from", status().name(), "action", action.name()));
  }

  // ------------------------------------------------------------------ the states

  record Draft() implements CourseState {
    @Override
    public CourseStatus status() {
      return CourseStatus.DRAFT;
    }

    @Override
    public CourseStatus submit() {
      return CourseStatus.IN_REVIEW;
    }
  }

  record InReview() implements CourseState {
    @Override
    public CourseStatus status() {
      return CourseStatus.IN_REVIEW;
    }

    @Override
    public CourseStatus publish() {
      return CourseStatus.PUBLISHED;
    }

    @Override
    public CourseStatus withdraw() {
      return CourseStatus.DRAFT;
    }
  }

  record Published() implements CourseState {
    @Override
    public CourseStatus status() {
      return CourseStatus.PUBLISHED;
    }

    @Override
    public CourseStatus unpublish() {
      return CourseStatus.DRAFT;
    }
  }

  /** Terminal: no transition out, no edits. Bring one back by duplicating it (Prototype). */
  record Archived() implements CourseState {
    @Override
    public CourseStatus status() {
      return CourseStatus.ARCHIVED;
    }

    @Override
    public CourseStatus archive() {
      throw illegalFromArchived();
    }

    @Override
    public boolean acceptsEdits() {
      return false;
    }

    private ConflictException illegalFromArchived() {
      return new ConflictException(
          "ILLEGAL_TRANSITION",
          "The course is already archived.",
          Map.of("from", "ARCHIVED", "action", "ARCHIVE"));
    }
  }

  /** The persisted status → its state object (states are stateless values, so records). */
  static CourseState of(CourseStatus status) {
    return switch (status) {
      case DRAFT -> new Draft();
      case IN_REVIEW -> new InReview();
      case PUBLISHED -> new Published();
      case ARCHIVED -> new Archived();
    };
  }
}
