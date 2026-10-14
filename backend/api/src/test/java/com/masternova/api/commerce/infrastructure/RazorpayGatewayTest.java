package com.masternova.api.commerce.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.masternova.api.commerce.domain.PaymentGateway.CheckoutCallback;
import com.masternova.api.commerce.domain.PaymentProviderException;
import com.masternova.kernel.money.Money;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Adapter's translations against a mocked Razorpay: the requests it sends (paise, receipt,
 * basic auth from the factory) and how each failure becomes temporary or permanent. No Spring
 * context, no network.
 */
class RazorpayGatewayTest {

  static final String BASE = "https://api.razorpay.test";
  final UUID orderId = UUID.randomUUID();
  MockRestServiceServer razorpay;
  RazorpayGateway gateway;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl(BASE).defaultHeader("Authorization", "Basic a2V5");
    razorpay = MockRestServiceServer.bindTo(builder).build();
    gateway =
        new RazorpayGateway(
            builder.build(),
            "rzp_test_key",
            new RazorpaySignatures("key-secret", "webhook-secret"),
            JsonMapper.builder().build());
  }

  @Test
  void createsAnOrderInPaiseWithOurIdAsTheReceipt() {
    razorpay
        .expect(requestTo(BASE + "/v1/orders"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Basic a2V5"))
        .andExpect(jsonPath("$.amount").value(49_900))
        .andExpect(jsonPath("$.currency").value("INR"))
        .andExpect(jsonPath("$.receipt").value(orderId.toString()))
        .andRespond(
            withSuccess(
                "{\"id\": \"order_N1\", \"entity\": \"order\", \"amount\": 49900, \"status\": \"created\"}",
                MediaType.APPLICATION_JSON));

    assertThat(gateway.createOrder(orderId, Money.of(49_900, "INR"))).isEqualTo("order_N1");
    razorpay.verify();
  }

  @Test
  void refundsTheWholePayment() {
    razorpay
        .expect(requestTo(BASE + "/v1/payments/pay_B/refund"))
        .andExpect(jsonPath("$.amount").value(49_900))
        .andRespond(
            withSuccess(
                "{\"id\": \"rfnd_C\", \"entity\": \"refund\", \"status\": \"processed\"}",
                MediaType.APPLICATION_JSON));

    assertThat(gateway.refund("pay_B", Money.of(49_900, "INR"), orderId)).isEqualTo("rfnd_C");
  }

  @Test
  void aServerErrorOrRateLimitIsTemporary() {
    razorpay.expect(requestTo(BASE + "/v1/orders")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
    assertThatThrownBy(() -> gateway.createOrder(orderId, Money.of(1, "INR")))
        .isInstanceOfSatisfying(
            PaymentProviderException.class, e -> assertThat(e.isTemporary()).isTrue());

    razorpay.reset();
    razorpay
        .expect(requestTo(BASE + "/v1/orders"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    assertThatThrownBy(() -> gateway.createOrder(orderId, Money.of(1, "INR")))
        .isInstanceOfSatisfying(
            PaymentProviderException.class, e -> assertThat(e.isTemporary()).isTrue());
  }

  @Test
  void aTimeoutIsTemporary() {
    razorpay
        .expect(requestTo(BASE + "/v1/orders"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));
    assertThatThrownBy(() -> gateway.createOrder(orderId, Money.of(1, "INR")))
        .isInstanceOfSatisfying(
            PaymentProviderException.class, e -> assertThat(e.isTemporary()).isTrue());
  }

  @Test
  void aRejectedRequestIsPermanentAndSaysWhy() {
    razorpay
        .expect(requestTo(BASE + "/v1/payments/pay_B/refund"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"error\": {\"code\": \"BAD_REQUEST_ERROR\", \"description\": \"The payment has been fully refunded already\"}}"));

    assertThatThrownBy(() -> gateway.refund("pay_B", Money.of(1, "INR"), orderId))
        .isInstanceOfSatisfying(
            PaymentProviderException.class,
            e -> {
              assertThat(e.isTemporary()).isFalse();
              assertThat(e.getMessage()).contains("400", "fully refunded already");
            });
  }

  @Test
  void anAnswerWithoutAnIdIsNotTrusted() throws IOException {
    razorpay
        .expect(requestTo(BASE + "/v1/orders"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> gateway.createOrder(orderId, Money.of(1, "INR")))
        .isInstanceOf(PaymentProviderException.class);
  }

  @Test
  void aSuccessThatIsntJsonIsStillOurException() {
    // review fix: Jackson's exception escaped the adapter, and checkout left the order PENDING
    razorpay
        .expect(requestTo(BASE + "/v1/orders"))
        .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));
    assertThatThrownBy(() -> gateway.createOrder(orderId, Money.of(1, "INR")))
        .isInstanceOfSatisfying(
            PaymentProviderException.class, e -> assertThat(e.isTemporary()).isFalse());
  }

  @Test
  void verifiesTheBrowsersCallbackWithTheKeySecret() {
    String signature = new RazorpaySignatures("key-secret", "x").signCheckout("order_A", "pay_B");

    assertThat(gateway.isAuthentic(new CheckoutCallback("order_A", "pay_B", signature))).isTrue();
    assertThat(gateway.isAuthentic(new CheckoutCallback("order_A", "pay_Z", signature))).isFalse();
    assertThat(gateway.publicKey()).isEqualTo("rzp_test_key");
  }
}
