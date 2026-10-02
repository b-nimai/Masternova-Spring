package com.masternova.java.sealed;

import com.masternova.java.valueobject.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * The result of asking a payment provider to charge a card — exactly one of three shapes.
 *
 * <p>Study note: {@code patterns/java/02-sealed-types-and-pattern-matching.md}.
 */
// ⭐ `sealed` = a CLOSED hierarchy: only the types listed (or, as here, nested in the same file)
//    may implement this interface. The compiler therefore knows every possible case, which is
//    what lets a `switch` over PaymentOutcome be checked for exhaustiveness.
//    No `permits` clause is needed because all implementations are in this file.
public sealed interface PaymentOutcome {

  // ⭐ Records are implicitly `final`, so they satisfy the rule "every permitted subtype must be
  //    final, sealed, or non-sealed" with no extra keyword.

  /** The money was taken. */
  record Captured(String paymentId, Money amount) implements PaymentOutcome {
    public Captured {
      Objects.requireNonNull(paymentId, "paymentId");
      Objects.requireNonNull(amount, "amount");
    }
  }

  /** The provider refused. {@code retryable} = a temporary problem (network, bank timeout). */
  record Failed(String errorCode, boolean retryable) implements PaymentOutcome {
    public Failed {
      Objects.requireNonNull(errorCode, "errorCode");
    }
  }

  /** Waiting for the customer or bank (e.g. UPI approval on the phone) until {@code expiresAt}. */
  record Pending(String paymentId, Instant expiresAt) implements PaymentOutcome {
    public Pending {
      Objects.requireNonNull(paymentId, "paymentId");
      Objects.requireNonNull(expiresAt, "expiresAt");
    }
  }
}
