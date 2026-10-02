package com.masternova.worker.notification.mail;

/**
 * The provider REFUSED this recipient for good (e.g. SMTP 550 "mailbox unavailable", Resend 422).
 * Retrying would only hurt the sender's reputation — the address gets suppressed instead.
 */
public class PermanentDeliveryException extends RuntimeException {

  public PermanentDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
