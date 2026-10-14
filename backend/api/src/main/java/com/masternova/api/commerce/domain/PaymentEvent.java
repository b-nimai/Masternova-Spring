package com.masternova.api.commerce.domain;

import com.masternova.kernel.money.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * What a messenger — the browser's signed callback, a webhook, our own refund call — says happened
 * to a payment, provider-neutral (LLD §3). Gateways translate their payloads into these.
 */
public sealed interface PaymentEvent {

  /**
   * The money was taken.
   *
   * @param amount what the provider says it captured; empty when the messenger doesn't say (the
   *     browser's callback carries only a signature)
   */
  record Captured(String paymentId, Optional<Money> amount) implements PaymentEvent {
    public Captured {
      Objects.requireNonNull(paymentId, "paymentId");
      Objects.requireNonNull(amount, "amount");
    }
  }

  /** One attempt failed. The provider's order stays open: the buyer may try again. */
  record PaymentFailed(Optional<String> paymentId, String reason) implements PaymentEvent {
    public PaymentFailed {
      Objects.requireNonNull(paymentId, "paymentId");
      Objects.requireNonNull(reason, "reason");
    }
  }

  /** The money went back (all of it: partial refunds are not supported). */
  record Refunded(String refundId) implements PaymentEvent {
    public Refunded {
      Objects.requireNonNull(refundId, "refundId");
    }
  }
}
