package com.masternova.api.commerce.domain;

import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The payment provider, as commerce needs it (LLD §4): a PORT in our words, with no HTTP or vendor
 * type in it. Implementations translate (Adapter) and are chosen by configuration (Strategy).
 *
 * <p>⭐ Force: the provider is a deployment choice — Razorpay in test or live mode, or a
 * network-free double for local runs and e2e that have no Razorpay keys. Checkout, confirm,
 * webhooks and refunds never say {@code if (fake)}.
 *
 * <p>Calls that reach the network ({@link #createOrder}, {@link #refund}) are made with NO database
 * transaction open (LLD §5) and throw {@link PaymentProviderException} when the provider fails.
 */
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "Strategy: the provider, picked by configuration",
    note = "patterns/docs/01-strategy.md")
public interface PaymentGateway {

  /** Stored on the order: {@code RAZORPAY}, {@code FAKE}. */
  String provider();

  /** The public key the browser's checkout widget needs (Razorpay's {@code key_id}). */
  String publicKey();

  /**
   * Opens the provider's order for this amount. {@code orderId} goes along as the provider's
   * receipt, so the provider's dashboard points back at our order.
   *
   * @return the provider's order id
   */
  String createOrder(UUID orderId, Money amount);

  /**
   * Whether the browser's callback really came from the provider's checkout: a signature over the
   * provider order id and the payment id, with the key secret. Constant-time.
   */
  boolean isAuthentic(CheckoutCallback callback);

  /**
   * Verifies a webhook's signature over its RAW body, then translates it.
   *
   * @param headers lower-cased header names
   * @throws InvalidSignatureException when the signature doesn't verify — nothing was parsed
   */
  WebhookEvent parseWebhook(byte[] body, Map<String, String> headers);

  /**
   * Gives the whole payment back.
   *
   * @return the provider's refund id
   */
  String refund(String paymentId, Money amount, UUID orderId);

  /** What the provider's checkout hands the browser once the buyer has paid. */
  record CheckoutCallback(String providerOrderId, String paymentId, String signature) {
    public CheckoutCallback {
      Objects.requireNonNull(providerOrderId, "providerOrderId");
      Objects.requireNonNull(paymentId, "paymentId");
      Objects.requireNonNull(signature, "signature");
    }
  }

  /**
   * A verified webhook, translated (LLD §3).
   *
   * @param eventId the provider's id for this delivery's EVENT — the dedupe key (ADR-0018)
   * @param type the provider's event name, for logs and the inbox
   * @param providerOrderId which order it is about, if any
   * @param event what it means for that order; empty for events commerce doesn't act on
   */
  record WebhookEvent(
      String eventId, String type, Optional<String> providerOrderId, Optional<PaymentEvent> event) {
    public WebhookEvent {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(providerOrderId, "providerOrderId");
      Objects.requireNonNull(event, "event");
    }
  }

  /** A signature that doesn't verify: the message is not from the provider. Never retried. */
  final class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException(String message) {
      super(message);
    }
  }
}
