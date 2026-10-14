package com.masternova.api.commerce.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Razorpay's two signature schemes, both hex HMAC-SHA256 (LLD §2, Security):
 *
 * <ul>
 *   <li>the browser's callback: {@code HMAC(order_id + "|" + payment_id, key_secret)};
 *   <li>a webhook: {@code HMAC(raw body, webhook_secret)} in {@code X-Razorpay-Signature}.
 * </ul>
 *
 * <p>⭐ The webhook MAC is over the RAW BYTES as received. Parsing first and re-serialising would
 * change whitespace and key order, and no signature would ever verify — or worse, a lenient parser
 * would let a forged body through. Comparison is constant-time ({@link MessageDigest#isEqual}), so
 * the time it takes leaks nothing about how much of a guess was right.
 */
final class RazorpaySignatures {

  private static final String ALGORITHM = "HmacSHA256";
  private static final HexFormat HEX = HexFormat.of();

  private final byte[] keySecret;
  private final byte[] webhookSecret;

  RazorpaySignatures(String keySecret, String webhookSecret) {
    this.keySecret = keySecret.getBytes(StandardCharsets.UTF_8);
    this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
  }

  String signCheckout(String providerOrderId, String paymentId) {
    return hex(keySecret, (providerOrderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
  }

  boolean isCheckoutAuthentic(String providerOrderId, String paymentId, String signature) {
    return matches(signCheckout(providerOrderId, paymentId), signature);
  }

  String signWebhook(byte[] body) {
    return hex(webhookSecret, body);
  }

  boolean isWebhookAuthentic(byte[] body, String signature) {
    return signature != null && matches(signWebhook(body), signature);
  }

  private static boolean matches(String expected, String given) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), given.getBytes(StandardCharsets.US_ASCII));
  }

  private static String hex(byte[] secret, byte[] data) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      return HEX.formatHex(mac.doFinal(data));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is always available", e);
    }
  }
}
