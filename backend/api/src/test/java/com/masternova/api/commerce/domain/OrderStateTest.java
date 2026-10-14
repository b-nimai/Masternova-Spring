package com.masternova.api.commerce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.commerce.domain.PaymentEvent.Captured;
import com.masternova.api.commerce.domain.PaymentEvent.PaymentFailed;
import com.masternova.api.commerce.domain.PaymentEvent.Refunded;
import com.masternova.api.commerce.domain.Transition.Effect;
import com.masternova.api.commerce.domain.Transition.Ignore;
import com.masternova.api.commerce.domain.Transition.Move;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The State pattern's twelve answers, as the LLD's table (§3), then the properties that make the
 * machine safe against late and reordered messages. No Spring, no database.
 */
class OrderStateTest {

  static final Captured CAPTURED = new Captured("pay_1", Optional.empty());
  static final PaymentFailed FAILED = new PaymentFailed(Optional.of("pay_0"), "card declined");
  static final Refunded REFUNDED = new Refunded("rfnd_1");
  static final List<PaymentEvent> EVENTS = List.of(CAPTURED, FAILED, REFUNDED);

  /** ⭐ The LLD's table, row by row: state, event → target (null = ignored), effect. */
  static Stream<Arguments> table() {
    return Stream.of(
        Arguments.of(OrderStatus.PENDING, CAPTURED, OrderStatus.PAID, Effect.GRANT),
        Arguments.of(OrderStatus.PENDING, FAILED, OrderStatus.FAILED, Effect.NONE),
        Arguments.of(OrderStatus.PENDING, REFUNDED, OrderStatus.REFUNDED, Effect.NONE),
        Arguments.of(OrderStatus.FAILED, CAPTURED, OrderStatus.PAID, Effect.GRANT),
        Arguments.of(OrderStatus.FAILED, FAILED, null, null),
        Arguments.of(OrderStatus.FAILED, REFUNDED, OrderStatus.REFUNDED, Effect.NONE),
        Arguments.of(OrderStatus.PAID, CAPTURED, null, null),
        Arguments.of(OrderStatus.PAID, FAILED, null, null),
        Arguments.of(OrderStatus.PAID, REFUNDED, OrderStatus.REFUNDED, Effect.REVOKE),
        Arguments.of(OrderStatus.REFUNDED, CAPTURED, null, null),
        Arguments.of(OrderStatus.REFUNDED, FAILED, null, null),
        Arguments.of(OrderStatus.REFUNDED, REFUNDED, null, null));
  }

  @ParameterizedTest(name = "{0} + {1} → {2} ({3})")
  @MethodSource("table")
  void everyCellOfTheTable(OrderStatus from, PaymentEvent event, OrderStatus to, Effect effect) {
    Transition t = OrderState.of(from).on(event);
    if (to == null) {
      assertThat(t).isInstanceOf(Ignore.class);
      assertThat(((Ignore) t).state()).isEqualTo(from);
    } else {
      assertThat(t).isEqualTo(new Move(from, to, effect));
    }
  }

  @Test
  void theTableCoversEveryStateAndEvent() {
    assertThat(table()).hasSize(OrderStatus.values().length * EVENTS.size());
  }

  @Test
  void everyMoveGoesForwardAndIsInTheTransitionTable() {
    for (OrderStatus from : OrderStatus.values()) {
      for (PaymentEvent event : EVENTS) {
        if (OrderState.of(from).on(event) instanceof Move m) {
          assertThat(m.to().rank()).as("%s + %s", from, event).isGreaterThan(from.rank());
          assertThat(from.canMoveTo(m.to())).as("%s → %s in the table", from, m.to()).isTrue();
        }
      }
    }
  }

  @Test
  void everyEdgeOfTheTableIsReachableBySomeEvent() {
    // the other direction: the table has no edge the states never take (no dead documentation)
    for (OrderStatus from : OrderStatus.values()) {
      Set<OrderStatus> reached = EnumSet.noneOf(OrderStatus.class);
      EVENTS.forEach(
          e -> {
            if (OrderState.of(from).on(e) instanceof Move m) {
              reached.add(m.to());
            }
          });
      assertThat(reached).as("successors of %s", from).isEqualTo(from.successors());
    }
  }

  @Test
  void onlyARefundFromPaidRevokesAndOnlyACaptureGrants() {
    for (OrderStatus from : OrderStatus.values()) {
      for (PaymentEvent event : EVENTS) {
        if (OrderState.of(from).on(event) instanceof Move m) {
          Effect expected =
              event instanceof Captured
                  ? Effect.GRANT
                  : event instanceof Refunded && from == OrderStatus.PAID
                      ? Effect.REVOKE
                      : Effect.NONE;
          assertThat(m.effect()).as("%s + %s", from, event).isEqualTo(expected);
        }
      }
    }
  }

  @Test
  void anyOrderOfMessagesEndsInTheSameStateAndGrantsAtMostOnce() {
    // ⭐ out-of-order safety, exhaustively: every permutation of {captured, failed, refunded}
    //    plus duplicates. Whatever arrives first, a refunded payment ends REFUNDED, and access is
    //    granted at most once and revoked only if it was granted.
    List<List<PaymentEvent>> histories =
        List.of(
            List.of(CAPTURED, FAILED, REFUNDED),
            List.of(CAPTURED, REFUNDED, FAILED),
            List.of(FAILED, CAPTURED, REFUNDED),
            List.of(FAILED, REFUNDED, CAPTURED),
            List.of(REFUNDED, CAPTURED, FAILED),
            List.of(REFUNDED, FAILED, CAPTURED),
            List.of(CAPTURED, CAPTURED, REFUNDED, REFUNDED, CAPTURED));
    for (List<PaymentEvent> history : histories) {
      Order order = Orders.pending();
      int grants = 0;
      int revokes = 0;
      for (PaymentEvent e : history) {
        if (order.apply(e, Orders.PLACED) instanceof Move m) {
          grants += m.effect() == Effect.GRANT ? 1 : 0;
          revokes += m.effect() == Effect.REVOKE ? 1 : 0;
        }
      }
      assertThat(order.status()).as("%s", history).isEqualTo(OrderStatus.REFUNDED);
      assertThat(grants).as("%s", history).isLessThanOrEqualTo(1);
      assertThat(revokes).as("%s", history).isEqualTo(grants); // every grant taken back, once
    }
  }
}
