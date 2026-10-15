package com.masternova.api.commerce.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Both of Razorpay's schemes, checked against an independent HMAC computation. */
class RazorpaySignaturesTest {

  final RazorpaySignatures signatures = new RazorpaySignatures("key-secret", "webhook-secret");

  static String hmacHex(String secret, byte[] data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(data));
  }

  @Test
  void theCheckoutSignatureIsOverOrderPipePaymentWithTheKeySecret() throws Exception {
    String expected = hmacHex("key-secret", "order_A|pay_B".getBytes(StandardCharsets.UTF_8));

    assertThat(signatures.signCheckout("order_A", "pay_B")).isEqualTo(expected);
    assertThat(signatures.isCheckoutAuthentic("order_A", "pay_B", expected)).isTrue();
    assertThat(signatures.isCheckoutAuthentic("order_A", "pay_C", expected)).isFalse(); // swapped
    assertThat(signatures.isCheckoutAuthentic("order_X", "pay_B", expected)).isFalse(); // replayed
    assertThat(signatures.isCheckoutAuthentic("order_A", "pay_B", expected.toUpperCase()))
        .isFalse();
    assertThat(signatures.isCheckoutAuthentic("order_A", "pay_B", "")).isFalse();
  }

  @Test
  void theWebhookSignatureIsOverTheRawBytesWithTheWebhookSecret() throws Exception {
    byte[] body = RazorpayFixtures.captured("order_A", "pay_B", 49_900);
    String expected = hmacHex("webhook-secret", body);

    assertThat(signatures.isWebhookAuthentic(body, expected)).isTrue();
    // ⭐ the same JSON with one space more is a different message
    byte[] reformatted =
        (new String(body, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8);
    assertThat(signatures.isWebhookAuthentic(reformatted, expected)).isFalse();
    // the key secret doesn't sign webhooks
    assertThat(signatures.isWebhookAuthentic(body, hmacHex("key-secret", body))).isFalse();
    assertThat(signatures.isWebhookAuthentic(body, null)).isFalse();
  }
}
