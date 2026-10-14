package com.masternova.api.commerce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.infrastructure.FakePaymentGateway;
import com.masternova.api.commerce.infrastructure.RazorpayFixtures;
import com.masternova.api.entitlement.TestCourses;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⭐ Phase 9.8, the interview proof: "webhooks are exactly-once in effect". A real server (Tomcat,
 * virtual threads), real Postgres and Redis; every copy released at the same instant by a latch.
 *
 * <p>"Exactly once in effect" is checked at every place an effect could double: the order moves
 * once, the entitlement row is written once, ONE {@code OrderPaid} and ONE {@code
 * EntitlementGranted} reach the outbox (so the receipt is emailed once and Phase 10 enrolls once),
 * and the inbox holds one claim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExactlyOnceIT {

  static final int COPIES = 50;

  @LocalServerPort int port;
  @Autowired DataSource dataSource;
  @Autowired CheckoutService checkout;
  @Autowired FakePaymentGateway provider;
  @Autowired JsonMapper json;
  final HttpClient http = HttpClient.newHttpClient();
  JdbcClient jdbc;
  UUID learner;
  UUID course;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course =
        new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null).courseId();
  }

  @AfterEach
  void clean() {
    jdbc.sql("DELETE FROM payment_webhook_event").update();
    new TestInstructors(dataSource).deleteAll();
  }

  private Order placeOrder() {
    return checkout.checkout(learner, List.of(course), Optional.empty()).order();
  }

  private String postWebhook(String eventId, byte[] body) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/v1/webhooks/payments"))
                .header("Content-Type", "application/json")
                .header("X-Razorpay-Event-Id", eventId)
                .header("X-Razorpay-Signature", provider.signWebhook(body))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return json.readTree(response.body()).path("outcome").asString();
  }

  /** Runs every task at the same instant (a latch), on its own thread; returns their results. */
  private static List<String> all(List<Callable<String>> tasks) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(tasks.size())) {
      for (Callable<String> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      List<String> results = new ArrayList<>();
      for (Future<String> f : futures) {
        results.add(f.get());
      }
      return results;
    }
  }

  private long count(String sql) {
    return jdbc.sql(sql).param("u", learner.toString()).query(Long.class).single();
  }

  private void assertExactlyOneEffect(Order order) {
    assertThat(
            jdbc.sql("SELECT status FROM purchase_order WHERE id = :id")
                .param("id", order.id())
                .query(String.class)
                .single())
        .isEqualTo("PAID");
    assertThat(count("SELECT count(*) FROM entitlement WHERE user_id = CAST(:u AS uuid)")).isOne();
    assertThat(
            count(
                "SELECT count(*) FROM outbox_message WHERE event_type = 'commerce.order-paid.v1'"
                    + " AND payload->>'userId' = :u"))
        .as("receipts that will be emailed")
        .isOne();
    assertThat(
            count(
                "SELECT count(*) FROM outbox_message WHERE event_type = 'entitlement.granted.v1'"
                    + " AND payload->>'userId' = :u"))
        .as("enrollments Phase 10 will create")
        .isOne();
  }

  @Test
  void fiftyConcurrentCopiesOfOneWebhookMakeExactlyOneEnrollment() throws Exception {
    Order order = placeOrder();
    byte[] captured =
        RazorpayFixtures.captured(order.providerOrderId().orElseThrow(), "pay_1", 49_900);

    List<Callable<String>> copies = new ArrayList<>();
    for (int i = 0; i < COPIES; i++) {
      copies.add(() -> postWebhook("evt_capture", captured));
    }
    Map<String, Long> outcomes =
        all(copies).stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    // ⭐ every copy got its 200; one did the work, 49 found the claim and did nothing
    assertThat(outcomes)
        .containsExactlyInAnyOrderEntriesOf(Map.of("PROCESSED", 1L, "DUPLICATE", 49L));
    assertThat(count("SELECT count(*) FROM payment_webhook_event")).isOne();
    assertExactlyOneEffect(order);
  }

  @Test
  void theBrowsersConfirmRacingDistinctWebhooksStillPaysOnce() throws Exception {
    // the realistic race: Razorpay sends payment.captured AND order.paid (different event ids, so
    // the inbox can't dedupe them), while the browser's handler confirms — all at once
    Order order = placeOrder();
    String providerOrderId = order.providerOrderId().orElseThrow();
    FakePaymentGateway.SimulatedPayment paid = provider.simulateCheckout(providerOrderId);
    byte[] captured = RazorpayFixtures.captured(providerOrderId, paid.paymentId(), 49_900);

    List<Callable<String>> racers = new ArrayList<>();
    for (int i = 0; i < COPIES / 2; i++) {
      String eventId = "evt_" + i; // 25 distinct events
      racers.add(() -> postWebhook(eventId, captured));
      racers.add(
          () ->
              checkout
                  .confirm(learner, order.id(), paid.paymentId(), paid.signature())
                  .status()
                  .name());
    }
    List<String> results = all(racers);

    // ⭐ the row lock serialises them all: whoever came first moved the order, the rest saw PAID
    assertThat(results).filteredOn("PROCESSED"::equals).hasSizeLessThanOrEqualTo(1);
    assertThat(results).filteredOn(r -> r.equals("PAID")).hasSize(COPIES / 2); // every confirm
    assertThat(count("SELECT count(*) FROM payment_webhook_event")).isEqualTo(COPIES / 2);
    assertExactlyOneEffect(order);
  }
}
