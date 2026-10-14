package com.masternova.api.commerce.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.PaymentGateway.InvalidSignatureException;
import com.masternova.api.commerce.domain.PaymentGateway.WebhookEvent;
import com.masternova.kernel.money.Money;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Razorpay's webhook format → our events, after the signature and never before it. */
class RazorpayWebhooksTest {

  final RazorpaySignatures signatures = new RazorpaySignatures("k", "webhook-secret");
  final RazorpayWebhooks webhooks = new RazorpayWebhooks(signatures, JsonMapper.builder().build());

  private WebhookEvent parse(byte[] body) {
    return webhooks.parse(
        body,
        Map.of(
            RazorpayWebhooks.SIGNATURE_HEADER,
            signatures.signWebhook(body),
            RazorpayWebhooks.EVENT_ID_HEADER,
            "evt_1"));
  }

  @Test
  void aCaptureCarriesTheAmount() {
    WebhookEvent e = parse(RazorpayFixtures.captured("order_A", "pay_B", 49_900));

    assertThat(e.eventId()).isEqualTo("evt_1");
    assertThat(e.type()).isEqualTo("payment.captured");
    assertThat(e.providerOrderId()).contains("order_A");
    assertThat(e.event())
        .contains(new PaymentEvent.Captured("pay_B", Optional.of(Money.of(49_900, "INR"))));
  }

  @Test
  void aFailureCarriesTheReason() {
    assertThat(parse(RazorpayFixtures.failed("order_A", "pay_B")).event())
        .contains(new PaymentEvent.PaymentFailed(Optional.of("pay_B"), "Card declined"));
  }

  @Test
  void aRefundFindsItsOrderThroughThePayment() {
    WebhookEvent e = parse(RazorpayFixtures.refunded("order_A", "pay_B", "rfnd_C"));

    assertThat(e.providerOrderId()).contains("order_A");
    assertThat(e.event()).contains(new PaymentEvent.Refunded("rfnd_C"));
  }

  @Test
  void otherEventsAreTranslatedToNothing() {
    WebhookEvent e = parse(RazorpayFixtures.other("payment.authorized"));
    assertThat(e.event()).isEmpty();
    assertThat(e.providerOrderId()).isEmpty();
  }

  @Test
  void aForgedBodyIsNeverParsed() {
    byte[] body = RazorpayFixtures.captured("order_A", "pay_B", 49_900);
    assertThatThrownBy(
            () -> webhooks.parse(body, Map.of(RazorpayWebhooks.SIGNATURE_HEADER, "00".repeat(32))))
        .isInstanceOf(InvalidSignatureException.class);
    assertThatThrownBy(() -> webhooks.parse("not json".getBytes(), Map.of()))
        .isInstanceOf(InvalidSignatureException.class); // no signature: not even looked at
  }

  @Test
  void withoutAnEventIdIdenticalBodiesAreOneEvent() {
    byte[] body = RazorpayFixtures.captured("order_A", "pay_B", 49_900);
    Map<String, String> headers =
        Map.of(RazorpayWebhooks.SIGNATURE_HEADER, signatures.signWebhook(body));

    assertThat(webhooks.parse(body, headers).eventId())
        .startsWith("sha256:")
        .isEqualTo(webhooks.parse(body.clone(), headers).eventId());
  }
}
