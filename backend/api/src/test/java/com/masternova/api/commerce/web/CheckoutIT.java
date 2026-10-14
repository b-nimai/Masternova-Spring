package com.masternova.api.commerce.web;

import static com.masternova.api.commerce.web.CartQuoteIT.as;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.commerce.domain.PaymentProviderException;
import com.masternova.api.commerce.infrastructure.FakePaymentGateway;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.identity.Role;
import com.masternova.kernel.money.Money;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checkout end to end with the FAKE provider (the default outside production): the order, the
 * browser's confirm, the grant in the same transaction (ADR-0019), the outbox event, the coupon
 * count, a free order, the Idempotency-Key, and a provider that is down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CheckoutIT.SwitchableProvider.class})
class CheckoutIT {

  /** The real fake gateway, which a test can switch "down" for its outbound calls. */
  @TestConfiguration
  static class SwitchableProvider {
    static final AtomicBoolean DOWN = new AtomicBoolean();

    @Bean
    @Primary
    PaymentGateway switchable(FakePaymentGateway real) {
      return new PaymentGateway() {
        public String provider() {
          return real.provider();
        }

        public String publicKey() {
          return real.publicKey();
        }

        public String createOrder(UUID orderId, Money amount) {
          if (DOWN.get()) {
            throw new PaymentProviderException("connect timed out", true, null);
          }
          return real.createOrder(orderId, amount);
        }

        public boolean isAuthentic(CheckoutCallback callback) {
          return real.isAuthentic(callback);
        }

        public WebhookEvent parseWebhook(byte[] body, Map<String, String> headers) {
          return real.parseWebhook(body, headers);
        }

        public String refund(String paymentId, Money amount, UUID orderId) {
          return real.refund(paymentId, amount, orderId);
        }
      };
    }
  }

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired JsonMapper json;
  JdbcClient jdbc;
  UUID learner;
  UUID admin;
  UUID java;
  UUID javaPaidLecture;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    SwitchableProvider.DOWN.set(false);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    admin = people.create("Ada Admin").id();
    var seeded = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
    java = seeded.courseId();
    javaPaidLecture = seeded.paidLecture();
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  private MockMvcTester.MockMvcRequestBuilder checkout(String key, String body) {
    return mvc.post()
        .uri("/api/v1/checkout")
        .with(as(learner, Role.LEARNER))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private JsonNode checkoutJava(String coupon) {
    String body =
        coupon == null
            ? "{\"courseIds\": [\"%s\"]}".formatted(java)
            : "{\"courseIds\": [\"%s\"], \"couponCode\": \"%s\"}".formatted(java, coupon);
    var result = checkout(UUID.randomUUID().toString(), body).exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return json.readTree(result.getResponse().getContentAsByteArray());
  }

  private JsonNode simulatePayment(String orderId) {
    var result =
        mvc.post()
            .uri("/api/v1/dev/fake-payments/{id}", orderId)
            .with(as(learner, Role.LEARNER))
            .exchange();
    assertThat(result).hasStatusOk();
    return json.readTree(result.getResponse().getContentAsByteArray());
  }

  private MockMvcTester.MockMvcRequestBuilder confirm(
      String orderId, String paymentId, String sig) {
    return mvc.post()
        .uri("/api/v1/orders/{id}/confirm", orderId)
        .with(as(learner, Role.LEARNER))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"paymentId\": \"%s\", \"signature\": \"%s\"}".formatted(paymentId, sig));
  }

  private String lectureAccess() {
    var result =
        mvc.get()
            .uri("/api/v1/lectures/{id}/access", javaPaidLecture)
            .with(as(learner, Role.LEARNER))
            .exchange();
    JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
    return node.path("kind").asString()
        + ":"
        + node.path("grant").asString(node.path("reason").asString());
  }

  private List<String> outboxTypes(String aggregateId) {
    return jdbc.sql(
            "SELECT event_type FROM outbox_message WHERE aggregate_id = :id ORDER BY occurred_at")
        .param("id", aggregateId)
        .query(String.class)
        .list();
  }

  @Test
  void payingAnOrderGrantsTheCourseInTheSameStep() {
    JsonNode placed = checkoutJava(null);
    String orderId = placed.path("order").path("id").asString();
    assertThat(placed.path("order").path("status").asString()).isEqualTo("PENDING");
    assertThat(placed.path("payment").path("provider").asString()).isEqualTo("FAKE");
    assertThat(placed.path("payment").path("amountMinor").asLong()).isEqualTo(49_900);
    assertThat(lectureAccess()).isEqualTo("DENIED:NO_ENTITLEMENT");

    JsonNode paid = simulatePayment(orderId);
    assertThat(
            confirm(orderId, paid.path("paymentId").asString(), paid.path("signature").asString()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PAID");

    assertThat(lectureAccess()).isEqualTo("ALLOWED:ENTITLED"); // ⭐ no eventual window
    assertThat(outboxTypes(orderId)).containsExactly("commerce.order-paid.v1");
    String payload =
        jdbc.sql("SELECT payload::text FROM outbox_message WHERE aggregate_id = :id")
            .param("id", orderId)
            .query(String.class)
            .single();
    assertThat(payload).contains(learner + "@catalog.test", "\"totalMinor\": 49900");
  }

  @Test
  void aSecondConfirmChangesNothing() {
    String orderId = checkoutJava(null).path("order").path("id").asString();
    JsonNode paid = simulatePayment(orderId);
    String paymentId = paid.path("paymentId").asString();
    String signature = paid.path("signature").asString();

    assertThat(confirm(orderId, paymentId, signature)).hasStatusOk();
    assertThat(confirm(orderId, paymentId, signature))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PAID");
    assertThat(outboxTypes(orderId)).hasSize(1);
  }

  @Test
  void aForgedOrStolenConfirmationIsRefused() {
    String orderId = checkoutJava(null).path("order").path("id").asString();
    JsonNode paid = simulatePayment(orderId);

    assertThat(confirm(orderId, "pay_other", paid.path("signature").asString()))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            mvc.post()
                .uri("/api/v1/orders/{id}/confirm", orderId)
                .with(as(admin, Role.ADMIN)) // not the buyer: the order "doesn't exist"
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\": \"p\", \"signature\": \"s\"}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(lectureAccess()).isEqualTo("DENIED:NO_ENTITLEMENT");
  }

  @Test
  void aRetriedCheckoutReplaysTheSameOrder() throws Exception {
    String body = "{\"courseIds\": [\"%s\"]}".formatted(java);
    var first = checkout("key-1", body).exchange();
    var second = checkout("key-1", body).exchange();

    assertThat(second).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "true");
    assertThat(second.getResponse().getContentAsString())
        .isEqualTo(first.getResponse().getContentAsString());
    assertThat(
            jdbc.sql("SELECT count(*) FROM purchase_order WHERE user_id = :u")
                .param("u", learner)
                .query(Long.class)
                .single())
        .isOne();
    assertThat(
            mvc.post()
                .uri("/api/v1/checkout")
                .with(as(learner, Role.LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .hasStatus(HttpStatus.BAD_REQUEST); // the key is required here
  }

  @Test
  void aHundredPercentCouponPaysAtOnceAndCountsTheRedemption() {
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/coupons")
                .with(as(admin, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"GIFT\", \"percentOff\": 100, \"maxRedemptions\": 5}"))
        .hasStatus(HttpStatus.CREATED);

    JsonNode placed = checkoutJava("gift");

    assertThat(placed.path("order").path("status").asString()).isEqualTo("PAID");
    assertThat(placed.path("order").path("totalMinor").asLong()).isZero();
    assertThat(placed.path("payment").isNull()).isTrue();
    assertThat(lectureAccess()).isEqualTo("ALLOWED:ENTITLED");
    assertThat(
            jdbc.sql("SELECT redemptions FROM coupon WHERE code = 'GIFT'")
                .query(Integer.class)
                .single())
        .isOne();
  }

  @Test
  void aProviderThatIsDownIsA503AndTheOrderFails() {
    SwitchableProvider.DOWN.set(true);

    assertThat(checkout("key-down", "{\"courseIds\": [\"%s\"]}".formatted(java)))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE");
    var orders = mvc.get().uri("/api/v1/orders").with(as(learner, Role.LEARNER));
    assertThat(orders).bodyJson().extractingPath("$[0].status").isEqualTo("FAILED");

    SwitchableProvider.DOWN.set(false); // ⭐ the 5xx released the key: the same key really retries
    assertThat(checkout("key-down", "{\"courseIds\": [\"%s\"]}".formatted(java)))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void anOrderIsOnlyItsBuyers() {
    String orderId = checkoutJava(null).path("order").path("id").asString();

    assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId).with(as(learner, Role.LEARNER)))
        .hasStatusOk();
    assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId).with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/v1/orders").with(as(learner, Role.LEARNER)))
        .bodyJson()
        .extractingPath("$[0].lines[0].title")
        .isEqualTo("Entitlement test");
  }
}
