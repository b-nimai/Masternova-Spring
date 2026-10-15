package com.masternova.patterns.state;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The third State in the project (Phase 9.3, simplified from {@code
 * com.masternova.api.commerce.domain.OrderState}): no event is ever REFUSED. Payment messages are
 * history replayed by machines, so a late or duplicate one is IGNORED. Two structures work
 * together: an {@code EnumSet} table says where an order may go (forward only), the sealed states
 * say what each event does there — including the EFFECT, which depends on the source state (a
 * refund revokes only from PAID).
 */
public final class OrderLifecycle {

  private OrderLifecycle() {}

  public enum Status {
    PENDING,
    FAILED,
    PAID,
    REFUNDED;

    static final Map<Status, Set<Status>> NEXT = new EnumMap<>(Status.class);

    static {
      NEXT.put(PENDING, EnumSet.of(FAILED, PAID, REFUNDED));
      NEXT.put(FAILED, EnumSet.of(PAID, REFUNDED));
      NEXT.put(PAID, EnumSet.of(REFUNDED));
      NEXT.put(REFUNDED, EnumSet.noneOf(Status.class));
    }
  }

  public enum Event {
    CAPTURED,
    FAILED,
    REFUNDED
  }

  public enum Effect {
    NONE,
    GRANT,
    REVOKE
  }

  /** {@code to == null}: ignored. */
  public record Answer(Status to, Effect effect) {
    static final Answer IGNORE = new Answer(null, Effect.NONE);
  }

  public sealed interface State {
    Answer on(Event e);
  }

  record Pending() implements State {
    public Answer on(Event e) {
      return switch (e) {
        case CAPTURED -> new Answer(Status.PAID, Effect.GRANT);
        case FAILED -> new Answer(Status.FAILED, Effect.NONE);
        case REFUNDED -> new Answer(Status.REFUNDED, Effect.NONE); // refund overtook the capture
      };
    }
  }

  record Failed() implements State {
    public Answer on(Event e) {
      return switch (e) {
        case CAPTURED -> new Answer(Status.PAID, Effect.GRANT);
        case FAILED -> Answer.IGNORE;
        case REFUNDED -> new Answer(Status.REFUNDED, Effect.NONE);
      };
    }
  }

  record Paid() implements State {
    public Answer on(Event e) {
      return e == Event.REFUNDED ? new Answer(Status.REFUNDED, Effect.REVOKE) : Answer.IGNORE;
    }
  }

  record Refunded() implements State {
    public Answer on(Event e) {
      return Answer.IGNORE; // terminal: a late capture never grants again
    }
  }

  public static State of(Status s) {
    return switch (s) {
      case PENDING -> new Pending();
      case FAILED -> new Failed();
      case PAID -> new Paid();
      case REFUNDED -> new Refunded();
    };
  }

  /** The Context: asks the state, checks the table, moves. */
  public static final class Order {
    private Status status = Status.PENDING;
    public int grants;
    public int revokes;

    public void apply(Event e) {
      Answer a = of(status).on(e);
      if (a.to() == null) {
        return;
      }
      if (!Status.NEXT.get(status).contains(a.to())) {
        throw new IllegalStateException(status + " → " + a.to());
      }
      status = a.to();
      grants += a.effect() == Effect.GRANT ? 1 : 0;
      revokes += a.effect() == Effect.REVOKE ? 1 : 0;
    }

    public Status status() {
      return status;
    }
  }
}
