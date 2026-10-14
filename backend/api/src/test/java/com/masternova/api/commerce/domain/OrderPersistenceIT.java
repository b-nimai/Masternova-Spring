package com.masternova.api.commerce.domain;

import static com.masternova.api.commerce.domain.Coupons.inr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.commerce.domain.PaymentEvent.Captured;
import com.masternova.api.entitlement.TestCourses;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The Order aggregate against the real schema (V19): mapping, lines, queries, CHECKs. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class OrderPersistenceIT {

  @Autowired TestEntityManager em;
  @Autowired OrderRepository orders;
  @Autowired DataSource dataSource;
  UUID buyer;
  UUID java;
  UUID spring;

  @BeforeEach
  void setUp() {
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    buyer = people.create("Lena Learner").id();
    TestCourses courses = new TestCourses(dataSource);
    java = courses.create(instructor, "PUBLISHED", 49_900, true, null).courseId();
    spring = courses.create(instructor, "PUBLISHED", 79_900, true, null).courseId();
  }

  private Order twoCourses() {
    Quote quote =
        new Quote(
            List.of(
                new Quote.Line(spring, "spring", "Spring", inr(79_900)),
                new Quote.Line(java, "java", "Java", inr(49_900))),
            inr(129_800),
            inr(0),
            inr(129_800),
            Optional.empty());
    return Order.place(UUID.randomUUID(), buyer, quote, "RAZORPAY", Orders.PLACED);
  }

  @Test
  void anOrderRoundTripsWithItsLinesInOrder() {
    Order placed = twoCourses();
    placed.attachProviderOrder("order_abc");
    orders.saveAndFlush(placed);
    em.clear();

    Order loaded = orders.findById(placed.id()).orElseThrow();
    assertThat(loaded.lines()).extracting(Order.Line::title).containsExactly("Spring", "Java");
    assertThat(loaded.total()).isEqualTo(inr(129_800));
    assertThat(loaded.providerOrderId()).contains("order_abc");
    assertThat(orders.lockByProviderOrderId("order_abc")).map(Order::id).contains(placed.id());
  }

  @Test
  void aPaymentIsPersisted() {
    Order order = orders.saveAndFlush(twoCourses());
    em.clear();

    Order locked = orders.lockById(order.id()).orElseThrow();
    locked.apply(new Captured("pay_1", Optional.of(inr(129_800))), Orders.PLACED.plusSeconds(5));
    orders.flush();
    em.clear();

    Order paid = orders.findById(order.id()).orElseThrow();
    assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
    assertThat(paid.providerPaymentId()).contains("pay_1");
  }

  @Test
  void myOrdersAreNewestFirstAndOnlyMine() {
    Order first = orders.save(twoCourses());
    Order second =
        orders.save(
            Order.place(
                UUID.randomUUID(),
                buyer,
                Orders.quote(java),
                "FAKE",
                Orders.PLACED.plusSeconds(1)));
    orders.flush();

    assertThat(orders.findByBuyerIdOrderByCreatedAtDesc(buyer, Limit.of(10)))
        .extracting(Order::id)
        .containsExactly(second.id(), first.id());
    assertThat(orders.findByIdAndBuyerId(first.id(), UUID.randomUUID())).isEmpty();
  }

  @Test
  void theSchemaRefusesAPaidOrderWithoutItsPayment() {
    Order order = orders.saveAndFlush(twoCourses());
    JdbcClient jdbc = JdbcClient.create(dataSource);

    assertThatThrownBy(
            () ->
                jdbc.sql("UPDATE purchase_order SET status = 'PAID' WHERE id = :id")
                    .param("id", order.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("purchase_order_paid_ck");
  }
}
