package com.masternova.patterns.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.patterns.state.OrderLifecycle.Event;
import com.masternova.patterns.state.OrderLifecycle.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest {

  @Test
  void anyOrderOfTheSameMessagesEndsTheSameAndGrantsAtMostOnce() {
    List<List<Event>> histories =
        List.of(
            List.of(Event.CAPTURED, Event.REFUNDED),
            List.of(Event.REFUNDED, Event.CAPTURED), // the refund's webhook arrived first
            List.of(Event.FAILED, Event.CAPTURED, Event.CAPTURED, Event.REFUNDED, Event.FAILED));
    for (List<Event> history : histories) {
      OrderLifecycle.Order order = new OrderLifecycle.Order();
      history.forEach(order::apply);
      assertThat(order.status()).isEqualTo(Status.REFUNDED);
      assertThat(order.grants).isLessThanOrEqualTo(1);
      assertThat(order.revokes).isEqualTo(order.grants);
    }
  }

  @Test
  void everyMoveIsInTheTable() {
    for (Status s : Status.values()) {
      for (Event e : Event.values()) {
        var answer = OrderLifecycle.of(s).on(e);
        if (answer.to() != null) {
          assertThat(Status.NEXT.get(s)).contains(answer.to());
          assertThat(answer.to().ordinal()).isGreaterThan(s.ordinal()); // forward only
        }
      }
    }
  }
}
