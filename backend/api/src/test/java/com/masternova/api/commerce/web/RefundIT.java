package com.masternova.api.commerce.web;

import static com.masternova.api.commerce.web.CartQuoteIT.as;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.commerce.infrastructure.FakePaymentGateway;
import com.masternova.api.commerce.infrastructure.RazorpayFixtures;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.identity.Role;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refunds (9.7): the admin's refund revokes every course in the order's transaction, and the events
 * that observers react to — {@code OrderRefunded} (the worker's email) and one {@code
 * EntitlementRevoked} per course (Phase 10's enrollment) — are in the outbox with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefundIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired FakePaymentGateway provider;
  @Autowired JsonMapper json;
  JdbcClient jdbc;
  UUID learner;
  UUID admin;
  TestCourses.Seeded java;
  TestCourses.Seeded spring;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    admin = people.create("Ada Admin").id();
    TestCourses courses = new TestCourses(dataSource);
    java = courses.create(instructor, "PUBLISHED", 49_900, true, null);
    spring = courses.create(instructor, "PUBLISHED", 79_900, true, null);
  }

  @AfterEach
  void clean() {
    jdbc.sql("DELETE FROM payment_webhook_event").update();
    new TestInstructors(dataSource).deleteAll();
  }

  private JsonNode body(MockMvcTester.MockMvcRequestBuilder request) throws Exception {
    var result = request.exchange();
    return json.readTree(result.getResponse().getContentAsByteArray());
  }

  /** Checks out both courses and pays through the browser's confirm. */
  private JsonNode paidOrder() throws Exception {
    JsonNode placed =
        body(
            mvc.post()
                .uri("/api/v1/checkout")
                .with(as(learner, Role.LEARNER))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"courseIds\": [\"%s\", \"%s\"]}"
                        .formatted(java.courseId(), spring.courseId())));
    String orderId = placed.path("order").path("id").asString();
    var paid = provider.simulateCheckout(placed.path("payment").path("providerOrderId").asString());
    assertThat(
            mvc.post()
                .uri("/api/v1/orders/{id}/confirm", orderId)
                .with(as(learner, Role.LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"paymentId\": \"%s\", \"signature\": \"%s\"}"
                        .formatted(paid.paymentId(), paid.signature())))
        .hasStatusOk();
    return placed;
  }

  private MockMvcTester.MockMvcRequestBuilder refund(String orderId, String key) {
    return mvc.post()
        .uri("/api/v1/admin/orders/{id}/refund", orderId)
        .with(as(admin, Role.ADMIN))
        .header("Idempotency-Key", key);
  }

  private String access(UUID lecture) throws Exception {
    JsonNode node =
        body(
            mvc.get().uri("/api/v1/lectures/{id}/access", lecture).with(as(learner, Role.LEARNER)));
    return node.path("kind").asString()
        + ":"
        + node.path("grant").asString(node.path("reason").asString());
  }

  private List<String> outbox(String type) {
    return jdbc.sql(
            "SELECT payload::text FROM outbox_message WHERE event_type = :type"
                + " AND payload->>'userId' = :u ORDER BY occurred_at")
        .param("type", type)
        .param("u", learner.toString())
        .query(String.class)
        .list();
  }

  @Test
  void aRefundRevokesEveryCourseAndTellsTheObservers() throws Exception {
    String orderId = paidOrder().path("order").path("id").asString();
    assertThat(access(java.paidLecture())).isEqualTo("ALLOWED:ENTITLED");
    assertThat(outbox("entitlement.granted.v1")).hasSize(2);

    JsonNode refunded = body(refund(orderId, "r-1"));

    assertThat(refunded.path("order").path("status").asString()).isEqualTo("REFUNDED");
    assertThat(refunded.path("providerRefundId").asString()).startsWith("rfnd_fake_");
    assertThat(access(java.paidLecture())).isEqualTo("DENIED:ENTITLEMENT_REVOKED");
    assertThat(access(spring.previewLecture())) // ⭐ deny wins: the refund beats the preview
        .isEqualTo("DENIED:ENTITLEMENT_REVOKED");
    assertThat(outbox("commerce.order-refunded.v1"))
        .singleElement()
        .asString()
        .contains("\"accessRevoked\": true", "\"amountMinor\": 129800");
    assertThat(outbox("entitlement.revoked.v1"))
        .hasSize(2)
        .allSatisfy(p -> assertThat(p).contains("\"reason\": \"refund\""));
  }

  @Test
  void aDoubleClickedRefundRefundsOnceAndTheProvidersWebhookIsIgnored() throws Exception {
    JsonNode placed = paidOrder();
    String orderId = placed.path("order").path("id").asString();

    assertThat(refund(orderId, "r-1")).hasStatusOk();
    assertThat(refund(orderId, "r-1")).hasStatusOk().hasHeader("Idempotent-Replayed", "true");
    assertThat(refund(orderId, "r-2")) // a new click on a refunded order
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ORDER_NOT_REFUNDABLE");

    String refundId =
        jdbc.sql("SELECT provider_refund_id FROM purchase_order WHERE id = :id")
            .param("id", UUID.fromString(orderId))
            .query(String.class)
            .single();
    byte[] webhook =
        RazorpayFixtures.refunded(
            placed.path("payment").path("providerOrderId").asString(), "pay_x", refundId);
    assertThat(
            mvc.post()
                .uri("/api/v1/webhooks/payments")
                .header("X-Razorpay-Event-Id", "evt_refund")
                .header("X-Razorpay-Signature", provider.signWebhook(webhook))
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhook))
        .bodyJson()
        .extractingPath("$.outcome")
        .isEqualTo("IGNORED");
    assertThat(outbox("commerce.order-refunded.v1")).hasSize(1);
    assertThat(outbox("entitlement.revoked.v1")).hasSize(2);
  }

  @Test
  void aCourseAlsoPaidForInAnotherOrderSurvivesTheRefund() throws Exception {
    // review fix: two tabs can buy one course twice ("already owned" is checked at quote, and
    // neither was paid yet); refunding one order must not take away what the other paid for
    String first = placeAndPayLater();
    String second = placeAndPayLater();
    pay(first);
    pay(second);

    assertThat(refund(first, "r-1")).hasStatusOk();

    assertThat(access(java.paidLecture())).isEqualTo("ALLOWED:ENTITLED");
    assertThat(outbox("entitlement.revoked.v1")).isEmpty();
  }

  @Test
  void aRefundInFlightRefusesASecondAndAStaleOneIsTakenOver() throws Exception {
    // review fix: two refunds with different keys both reached the provider
    String orderId = paidOrder().path("order").path("id").asString();
    jdbc.sql("UPDATE purchase_order SET refund_requested_at = now() WHERE id = :id")
        .param("id", UUID.fromString(orderId))
        .update(); // another request is refunding it right now

    assertThat(refund(orderId, "r-2"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("REFUND_IN_PROGRESS");

    jdbc.sql(
            "UPDATE purchase_order SET refund_requested_at = now() - interval '1 hour' WHERE id = :id")
        .param("id", UUID.fromString(orderId))
        .update(); // that request crashed an hour ago
    assertThat(refund(orderId, "r-3")).hasStatusOk();
  }

  /** Checks out Java alone and returns the order id, unpaid. */
  private String placeAndPayLater() throws Exception {
    return body(mvc.post()
            .uri("/api/v1/checkout")
            .with(as(learner, Role.LEARNER))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"courseIds\": [\"%s\"]}".formatted(java.courseId())))
        .path("order")
        .path("id")
        .asString();
  }

  private void pay(String orderId) throws Exception {
    JsonNode simulated =
        body(
            mvc.post()
                .uri("/api/v1/dev/fake-payments/{id}", orderId)
                .with(as(learner, Role.LEARNER)));
    assertThat(
            mvc.post()
                .uri("/api/v1/orders/{id}/confirm", orderId)
                .with(as(learner, Role.LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"paymentId\": \"%s\", \"signature\": \"%s\"}"
                        .formatted(
                            simulated.path("paymentId").asString(),
                            simulated.path("signature").asString())))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PAID");
  }

  @Test
  void onlyAPaidOrderAndOnlyAnAdmin() throws Exception {
    JsonNode placed =
        body(
            mvc.post()
                .uri("/api/v1/checkout")
                .with(as(learner, Role.LEARNER))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseIds\": [\"%s\"]}".formatted(java.courseId())));
    String pending = placed.path("order").path("id").asString();

    assertThat(refund(pending, "r-p")).hasStatus(HttpStatus.CONFLICT);
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/orders/{id}/refund", pending)
                .with(as(learner, Role.LEARNER))
                .header("Idempotency-Key", "r-l"))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/v1/admin/orders").with(as(admin, Role.ADMIN)))
        .bodyJson()
        .extractingPath("$[0].order.id")
        .isEqualTo(pending);
  }
}
