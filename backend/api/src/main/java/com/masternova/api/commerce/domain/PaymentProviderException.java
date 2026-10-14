package com.masternova.api.commerce.domain;

/**
 * The payment provider failed us (LLD §8). Not a {@code DomainException}: it isn't the caller's
 * mistake, and the platform's kinds are closed. Commerce's web layer maps it — temporary → 503
 * {@code PAYMENT_PROVIDER_UNAVAILABLE} with Retry-After, permanent → 502 {@code
 * PAYMENT_PROVIDER_ERROR}.
 */
public class PaymentProviderException extends RuntimeException {

  private final boolean temporary;

  public PaymentProviderException(String message, boolean temporary, Throwable cause) {
    super(message, cause);
    this.temporary = temporary;
  }

  /** Timeouts, connection failures, 5xx and 429: worth retrying later. */
  public boolean isTemporary() {
    return temporary;
  }
}
