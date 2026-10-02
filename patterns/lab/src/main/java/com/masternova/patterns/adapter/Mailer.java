package com.masternova.patterns.adapter;

/**
 * ⭐ TARGET: the interface OUR code is written against. It speaks our language ({@link Email}, a
 * message id, {@link Rejected}) and knows nothing about any vendor.
 *
 * <p>Failure contract: {@link Rejected} = retrying can never help; any other exception = temporary.
 */
public interface Mailer {

  /** Our shape of an email. */
  record Email(String to, String subject, String body) {}

  /** The vendor refused this recipient for good. */
  final class Rejected extends RuntimeException {
    public Rejected(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** @return the vendor's message id */
  String send(Email email);
}
