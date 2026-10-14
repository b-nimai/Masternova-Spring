package com.masternova.patterns.adapter.payment;

/**
 * The TARGET: commerce's port, in our words (Phase 9.4, simplified from {@code
 * com.masternova.api.commerce.domain.PaymentGateway}). No status codes, no vendor field names.
 */
public interface Payments {

  /** Our failure contract: is it worth trying again later? */
  final class ProviderFailed extends RuntimeException {
    public final boolean temporary;

    public ProviderFailed(String message, boolean temporary) {
      super(message);
      this.temporary = temporary;
    }
  }

  /**
   * @return the provider's order id
   */
  String openOrder(String ourOrderId, long amountMinor, String currency);
}
