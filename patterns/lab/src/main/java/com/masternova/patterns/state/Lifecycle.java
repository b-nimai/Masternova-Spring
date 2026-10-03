package com.masternova.patterns.state;

/**
 * One lifecycle (Draft → InReview → Published, any → Archived) written THREE ways, so the
 * trade-offs are side by side. The production version is the sealed one ({@code CourseState} in
 * the catalog); pattern note: patterns/docs/02-state.md.
 */
public final class Lifecycle {

  private Lifecycle() {}

  public enum Status {
    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    ARCHIVED
  }

  public enum Action {
    SUBMIT,
    PUBLISH,
    ARCHIVE
  }

  /** Thrown for an edge that isn't in the diagram. */
  public static final class IllegalTransition extends RuntimeException {
    public IllegalTransition(Object from, Action action) {
      super("can't " + action + " from " + from);
    }
  }

  // ----------------------------------------------------------------- 1. a switch table

  /**
   * ❌ for anything that grows: every rule of every state in ONE method. Fine for three states;
   * with ten it's the method everyone edits and nobody reads.
   */
  public static Status bySwitch(Status from, Action action) {
    return switch (from) {
      case DRAFT ->
          switch (action) {
            case SUBMIT -> Status.IN_REVIEW;
            case ARCHIVE -> Status.ARCHIVED;
            default -> throw new IllegalTransition(from, action);
          };
      case IN_REVIEW ->
          switch (action) {
            case PUBLISH -> Status.PUBLISHED;
            case ARCHIVE -> Status.ARCHIVED;
            default -> throw new IllegalTransition(from, action);
          };
      case PUBLISHED -> {
        if (action == Action.ARCHIVE) {
          yield Status.ARCHIVED;
        }
        throw new IllegalTransition(from, action);
      }
      case ARCHIVED -> throw new IllegalTransition(from, action);
    };
  }

  // ----------------------------------------------------------------- 2. enum constants with bodies

  /**
   * The classic Java State: each enum constant overrides the events it allows. Compact, and the
   * state IS the persisted value. Limits: one fixed set of singletons, no per-state data, and the
   * persistence enum now carries behaviour.
   */
  public enum EnumState {
    DRAFT {
      @Override
      EnumState submit() {
        return IN_REVIEW;
      }
    },
    IN_REVIEW {
      @Override
      EnumState publish() {
        return PUBLISHED;
      }
    },
    PUBLISHED,
    ARCHIVED {
      @Override
      EnumState archive() {
        throw new IllegalTransition(this, Action.ARCHIVE);
      }
    };

    EnumState submit() {
      throw new IllegalTransition(this, Action.SUBMIT);
    }

    EnumState publish() {
      throw new IllegalTransition(this, Action.PUBLISH);
    }

    EnumState archive() {
      return ARCHIVED;
    }

    public EnumState on(Action action) {
      return switch (action) {
        case SUBMIT -> submit();
        case PUBLISH -> publish();
        case ARCHIVE -> archive();
      };
    }
  }

  // ----------------------------------------------------------------- 3. sealed interface + defaults

  /**
   * ✅ What production uses. The interface's DEFAULT methods throw; each state (a record) overrides
   * only its legal events. Sealed → {@link #of} is an exhaustive switch, records → states can carry
   * data later (e.g. {@code InReview(reviewerId)}), and the persisted enum stays a plain enum.
   */
  public sealed interface State {
    Status status();

    default State submit() {
      throw new IllegalTransition(status(), Action.SUBMIT);
    }

    default State publish() {
      throw new IllegalTransition(status(), Action.PUBLISH);
    }

    default State archive() {
      return new Archived();
    }

    default State on(Action action) {
      return switch (action) {
        case SUBMIT -> submit();
        case PUBLISH -> publish();
        case ARCHIVE -> archive();
      };
    }

    static State of(Status status) {
      return switch (status) { // ⭐ exhaustive: a new Status won't compile without a State
        case DRAFT -> new Draft();
        case IN_REVIEW -> new InReview();
        case PUBLISHED -> new Published();
        case ARCHIVED -> new Archived();
      };
    }
  }

  public record Draft() implements State {
    public Status status() {
      return Status.DRAFT;
    }

    @Override
    public State submit() {
      return new InReview();
    }
  }

  public record InReview() implements State {
    public Status status() {
      return Status.IN_REVIEW;
    }

    @Override
    public State publish() {
      return new Published();
    }
  }

  public record Published() implements State {
    public Status status() {
      return Status.PUBLISHED;
    }
  }

  public record Archived() implements State {
    public Status status() {
      return Status.ARCHIVED;
    }

    @Override
    public State archive() {
      throw new IllegalTransition(status(), Action.ARCHIVE);
    }
  }
}
