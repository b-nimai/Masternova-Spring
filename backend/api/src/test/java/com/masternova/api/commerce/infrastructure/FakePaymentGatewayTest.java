package com.masternova.api.commerce.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.commerce.application.CommerceProperties;
import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.PaymentGateway.CheckoutCallback;
import com.masternova.kernel.money.Money;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The double: no network, but Razorpay's signatures and webhook format for real. */
class FakePaymentGatewayTest {

  final JsonMapper json = JsonMapper.builder().build();
  final FakePaymentGateway fake = new FakePaymentGateway("fake-key", "fake-hook", json);

  @Test
  void aSimulatedCheckoutPassesTheRealVerification() {
    String order = fake.createOrder(UUID.randomUUID(), Money.of(49_900, "INR"));
    var paid = fake.simulateCheckout(order);

    assertThat(order).startsWith("order_fake_");
    assertThat(fake.isAuthentic(new CheckoutCallback(order, paid.paymentId(), paid.signature())))
        .isTrue();
    assertThat(
            fake.isAuthentic(
                new CheckoutCallback("order_other", paid.paymentId(), paid.signature())))
        .isFalse();
  }

  @Test
  void itParsesRazorpayWebhooksItSigned() {
    byte[] body = RazorpayFixtures.captured("order_fake_1", "pay_1", 49_900);

    var event = fake.parseWebhook(body, Map.of("x-razorpay-signature", fake.signWebhook(body)));

    assertThat(event.event()).containsInstanceOf(PaymentEvent.Captured.class);
  }

  @Test
  void razorpayWithoutKeysRefusesToStart() {
    var noKeys =
        new CommerceProperties.Razorpay("", "", "", URI.create("https://api.razorpay.com"));
    assertThatThrownBy(() -> CommerceConfig.razorpay(noKeys, Duration.ofSeconds(1), json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RAZORPAY_KEY_ID");
  }
}
