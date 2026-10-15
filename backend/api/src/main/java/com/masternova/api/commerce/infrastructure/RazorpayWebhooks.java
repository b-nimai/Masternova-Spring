package com.masternova.api.commerce.infrastructure;

import com.masternova.api.commerce.domain.PaymentEvent;
import com.masternova.api.commerce.domain.PaymentGateway.InvalidSignatureException;
import com.masternova.api.commerce.domain.PaymentGateway.WebhookEvent;
import com.masternova.kernel.money.Money;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay's webhook format → our {@link WebhookEvent} (LLD §5). Verifies first, parses second:
 * nothing is read from a body whose signature failed.
 *
 * <p>Events acted on: {@code payment.captured} and {@code order.paid} (both mean the money is in),
 * {@code payment.failed}, {@code refund.processed}. Anything else is translated with no event, so
 * the endpoint answers 200 and Razorpay stops sending it (API conventions §12).
 */
final class RazorpayWebhooks {

  static final String SIGNATURE_HEADER = "x-razorpay-signature";
  static final String EVENT_ID_HEADER = "x-razorpay-event-id";

  private final RazorpaySignatures signatures;
  private final JsonMapper json;

  RazorpayWebhooks(RazorpaySignatures signatures, JsonMapper json) {
    this.signatures = signatures;
    this.json = json;
  }

  WebhookEvent parse(byte[] body, Map<String, String> headers) {
    if (!signatures.isWebhookAuthentic(body, headers.get(SIGNATURE_HEADER))) {
      throw new InvalidSignatureException("webhook signature does not verify");
    }
    JsonNode root = json.readTree(body);
    String type = text(root.path("event")).orElse("unknown");
    // ⭐ the dedupe key (ADR-0018); without the header, identical bodies are the same event
    String eventId =
        Optional.ofNullable(headers.get(EVENT_ID_HEADER)).orElseGet(() -> sha256(body));
    JsonNode payment = root.path("payload").path("payment").path("entity");
    JsonNode refund = root.path("payload").path("refund").path("entity");
    Optional<String> orderId =
        text(payment.path("order_id"))
            .or(() -> text(root.path("payload").path("order").path("entity").path("id")));

    Optional<PaymentEvent> event =
        switch (type) {
          case "payment.captured", "order.paid" ->
              text(payment.path("id")).map(id -> new PaymentEvent.Captured(id, amount(payment)));
          case "payment.failed" ->
              Optional.of(
                  new PaymentEvent.PaymentFailed(
                      text(payment.path("id")),
                      text(payment.path("error_description"))
                          .or(() -> text(payment.path("error_code")))
                          .orElse("payment failed")));
          case "refund.processed" ->
              text(refund.path("id")).map(id -> new PaymentEvent.Refunded(id, amount(refund)));
          default -> Optional.empty();
        };
    return new WebhookEvent(eventId, type, orderId, event);
  }

  /** A payment's or a refund's {@code amount} + {@code currency}, both in Razorpay's paise. */
  private static Optional<Money> amount(JsonNode entity) {
    return text(entity.path("currency"))
        .filter(c -> entity.path("amount").isIntegralNumber())
        .map(c -> Money.of(entity.path("amount").asLong(), c));
  }

  private static Optional<String> text(JsonNode node) {
    return node.isString() && !node.asString().isBlank()
        ? Optional.of(node.asString())
        : Optional.empty();
  }

  private static String sha256(byte[] body) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }
}
