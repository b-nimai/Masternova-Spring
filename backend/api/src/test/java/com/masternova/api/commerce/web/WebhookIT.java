package com.masternova.api.commerce.web;

import static com.masternova.api.commerce.web.CartQuoteIT.as;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.commerce.infrastructure.FakePaymentGateway;
import com.masternova.api.commerce.infrastructure.RazorpayFixtures;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.application.EntitlementService;
import com.masternova.api.identity.Role;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * The webhook over HTTP (9.6, ADR-0018): Razorpay-shaped bodies signed with the fake provider's
 * webhook secret, through the real parser, inbox and state machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WebhookIT.FlakyGrants.class})
class WebhookIT {

  /** Entitlement's real facade, whose grants a test can make fail. */
  @TestConfiguration
  static class FlakyGrants {
    static final AtomicBoolean FAIL = new AtomicBoolean();

    @Bean
    @Primary
    EntitlementApi flakyEntitlements(EntitlementService real) {
      return (EntitlementApi)
          java.lang.reflect.Proxy.newProxyInstance(
              EntitlementApi.class.getClassLoader(),
              new Class<?>[] {EntitlementApi.class},
              (proxy, method, args) -> {
                if (method.getName().equals("grant") && FAIL.get()) {
                  throw new IllegalStateException("the database said no");
                }
                try {
                  return method.invoke(real, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                  throw e.getCause();
                }
              });
    }
  }

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired FakePaymentGateway provider;
  @Autowired JsonMapper json;
  JdbcClient jdbc;
  UUID learner;
  UUID course;
  UUID paidLecture;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    FlakyGrants.FAIL.set(false);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    var seeded = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
    course = seeded.courseId();
    paidLecture = seeded.paidLecture();
  }

  @AfterEach
  void clean() {
    jdbc.sql("DELETE FROM payment_webhook_event").update();
    new TestInstructors(dataSource).deleteAll();
  }

  /** Checks out the course; returns the provider's order id. */
  private String placeOrder() throws Exception {
    var result =
        mvc.post()
            .uri("/api/v1/checkout")
            .with(as(learner, Role.LEARNER))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"courseIds\": [\"%s\"]}".formatted(course))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return json.readTree(result.getResponse().getContentAsByteArray())
        .path("payment")
        .path("providerOrderId")
        .asString();
  }

  MockMvcTester.MockMvcRequestBuilder webhook(String eventId, byte[] body) {
    return webhook(eventId, body, provider.signWebhook(body));
  }

  MockMvcTester.MockMvcRequestBuilder webhook(String eventId, byte[] body, String signature) {
    return mvc.post()
        .uri("/api/v1/webhooks/payments")
        .header("X-Razorpay-Event-Id", eventId)
        .header("X-Razorpay-Signature", signature)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String outcome(MockMvcTester.MockMvcRequestBuilder request) {
    var result = request.exchange();
    assertThat(result).hasStatusOk();
    return json.readTree(result.getResponse().getContentAsByteArray()).path("outcome").asString();
  }

  private String orderStatus(String providerOrderId) {
    return jdbc.sql("SELECT status FROM purchase_order WHERE provider_order_id = :id")
        .param("id", providerOrderId)
        .query(String.class)
        .single();
  }

  private String access() {
    var result =
        mvc.get()
            .uri("/api/v1/lectures/{id}/access", paidLecture)
            .with(as(learner, Role.LEARNER))
            .exchange();
    var node = json.readTree(result.getResponse().getContentAsByteArray());
    return node.path("kind").asString()
        + ":"
        + node.path("grant").asString(node.path("reason").asString());
  }

  private long paidEvents() {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_message WHERE event_type = 'commerce.order-paid.v1'"
                + " AND payload->>'userId' = :u")
        .param("u", learner.toString())
        .query(Long.class)
        .single();
  }

  @Test
  void aCaptureWebhookPaysTheOrderAndItsCopyDoesNothing() throws Exception {
    String order = placeOrder();
    byte[] captured = RazorpayFixtures.captured(order, "pay_1", 49_900);

    assertThat(outcome(webhook("evt_1", captured))).isEqualTo("PROCESSED");
    assertThat(orderStatus(order)).isEqualTo("PAID");
    assertThat(access()).isEqualTo("ALLOWED:ENTITLED");

    assertThat(outcome(webhook("evt_1", captured))).isEqualTo("DUPLICATE"); // same event again
    assertThat(outcome(webhook("evt_2", RazorpayFixtures.other("order.paid"))))
        .isEqualTo("IGNORED");
    assertThat(paidEvents()).isOne();
  }

  @Test
  void aForgedWebhookIsA400AndNothingIsClaimed() throws Exception {
    String order = placeOrder();
    byte[] captured = RazorpayFixtures.captured(order, "pay_1", 49_900);

    assertThat(webhook("evt_1", captured, "00".repeat(32)))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("WEBHOOK_SIGNATURE_INVALID");
    assertThat(
            mvc.post()
                .uri("/api/v1/webhooks/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(captured))
        .hasStatus(HttpStatus.BAD_REQUEST); // no signature at all
    assertThat(orderStatus(order)).isEqualTo("PENDING");
    assertThat(jdbc.sql("SELECT count(*) FROM payment_webhook_event").query(Long.class).single())
        .isZero();
    // ⭐ a real one with the same id afterwards is NOT a duplicate: the forgery claimed nothing
    assertThat(outcome(webhook("evt_1", captured))).isEqualTo("PROCESSED");
  }

  @Test
  void aRefundThatOvertakesTheCaptureNeverGrants() throws Exception {
    String order = placeOrder();

    assertThat(outcome(webhook("evt_r", RazorpayFixtures.refunded(order, "pay_1", "rfnd_1"))))
        .isEqualTo("PROCESSED");
    assertThat(outcome(webhook("evt_c", RazorpayFixtures.captured(order, "pay_1", 49_900))))
        .isEqualTo("IGNORED"); // late: the order is REFUNDED

    assertThat(orderStatus(order)).isEqualTo("REFUNDED");
    assertThat(access()).isEqualTo("DENIED:NO_ENTITLEMENT");
    assertThat(paidEvents()).isZero();
  }

  @Test
  void aFailedAttemptThenASuccessfulOne() throws Exception {
    String order = placeOrder();

    assertThat(outcome(webhook("evt_f", RazorpayFixtures.failed(order, "pay_0"))))
        .isEqualTo("PROCESSED");
    assertThat(orderStatus(order)).isEqualTo("FAILED");
    assertThat(outcome(webhook("evt_c", RazorpayFixtures.captured(order, "pay_1", 49_900))))
        .isEqualTo("PROCESSED");
    assertThat(orderStatus(order)).isEqualTo("PAID");
    // the first attempt's failure, redelivered late, can't undo it
    assertThat(outcome(webhook("evt_f2", RazorpayFixtures.failed(order, "pay_0"))))
        .isEqualTo("IGNORED");
    assertThat(orderStatus(order)).isEqualTo("PAID");
  }

  @Test
  void theWrongAmountIsIgnoredAndTheOrderStaysUnpaid() throws Exception {
    String order = placeOrder();

    assertThat(outcome(webhook("evt_1", RazorpayFixtures.captured(order, "pay_1", 100))))
        .isEqualTo("IGNORED");
    assertThat(orderStatus(order)).isEqualTo("PENDING");
    assertThat(access()).isEqualTo("DENIED:NO_ENTITLEMENT");
  }

  @Test
  void aPartialRefundFromTheDashboardRevokesNothing() throws Exception {
    // review fix: every refund.processed was a full refund, whatever its amount
    String order = placeOrder();
    webhook("evt_c", RazorpayFixtures.captured(order, "pay_1", 49_900)).exchange();

    assertThat(
            outcome(webhook("evt_r", RazorpayFixtures.refunded(order, "pay_1", "rfnd_1", 10_000))))
        .isEqualTo("IGNORED");
    assertThat(orderStatus(order)).isEqualTo("PAID");
    assertThat(access()).isEqualTo("ALLOWED:ENTITLED");
  }

  @Test
  void anotherSystemsOrderIsClaimedAndLeftAlone() {
    assertThat(outcome(webhook("evt_x", RazorpayFixtures.captured("order_not_ours", "pay_9", 1))))
        .isEqualTo("UNKNOWN_ORDER");
    assertThat(outcome(webhook("evt_x", RazorpayFixtures.captured("order_not_ours", "pay_9", 1))))
        .isEqualTo("DUPLICATE"); // claimed: the provider stops retrying it
  }

  @Test
  void aFailureRollsTheClaimBackSoTheRedeliveryRuns() throws Exception {
    String order = placeOrder();
    byte[] captured = RazorpayFixtures.captured(order, "pay_1", 49_900);

    FlakyGrants.FAIL.set(true);
    assertThat(webhook("evt_1", captured)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(orderStatus(order)).isEqualTo("PENDING"); // ⭐ not PAID without access
    assertThat(
            jdbc.sql("SELECT count(*) FROM payment_webhook_event WHERE event_id = 'evt_1'")
                .query(Long.class)
                .single())
        .isZero(); // ⭐ the claim rolled back with everything else

    FlakyGrants.FAIL.set(false);
    assertThat(outcome(webhook("evt_1", captured))).isEqualTo("PROCESSED"); // the redelivery
    assertThat(access()).isEqualTo("ALLOWED:ENTITLED");
    assertThat(
            jdbc.sql("SELECT outcome FROM payment_webhook_event WHERE event_id = 'evt_1'")
                .query(String.class)
                .single())
        .isEqualTo("PROCESSED");
  }
}
