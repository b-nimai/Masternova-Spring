package com.masternova.api.commerce.infrastructure;

import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.commerce.domain.PaymentSimulator;
import com.masternova.kernel.money.Money;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Razorpay double with NO network: local runs, e2e and CI have no Razorpay keys (LLD §6). ⭐ It
 * signs and verifies with Razorpay's exact schemes and parses Razorpay's webhook format, so
 * everything after the network — confirm, webhooks, the state machine — runs the same code as with
 * the real provider. Only the two outbound calls are simulated.
 *
 * <p>The browser can't pay a fake provider, so {@link #simulateCheckout} plays the provider's
 * checkout modal: it hands back what Razorpay's {@code handler} would. Exposed only through a dev
 * endpoint that exists only while this gateway is active.
 */
public class FakePaymentGateway implements PaymentGateway, PaymentSimulator {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final RazorpaySignatures signatures;
  private final RazorpayWebhooks webhooks;

  FakePaymentGateway(String keySecret, String webhookSecret, JsonMapper json) {
    this.signatures = new RazorpaySignatures(keySecret, webhookSecret);
    this.webhooks = new RazorpayWebhooks(signatures, json);
  }

  @Override
  public String provider() {
    return "FAKE";
  }

  @Override
  public String publicKey() {
    return "fake_key";
  }

  @Override
  public String createOrder(UUID orderId, Money amount) {
    return "order_fake_" + randomId();
  }

  @Override
  public boolean isAuthentic(CheckoutCallback callback) {
    return signatures.isCheckoutAuthentic(
        callback.providerOrderId(), callback.paymentId(), callback.signature());
  }

  @Override
  public WebhookEvent parseWebhook(byte[] body, Map<String, String> headers) {
    return webhooks.parse(body, headers);
  }

  @Override
  public String refund(String paymentId, Money amount, UUID orderId) {
    return "rfnd_fake_" + randomId();
  }

  /** Plays the checkout modal: a payment id and the signature Razorpay would send the browser. */
  @Override
  public SimulatedPayment simulateCheckout(String providerOrderId) {
    String paymentId = "pay_fake_" + randomId();
    return new SimulatedPayment(paymentId, signatures.signCheckout(providerOrderId, paymentId));
  }

  /** Signs a webhook body as Razorpay would — for tests and a dev's curl. */
  public String signWebhook(byte[] body) {
    return signatures.signWebhook(body);
  }

  private static String randomId() {
    byte[] bytes = new byte[7];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
