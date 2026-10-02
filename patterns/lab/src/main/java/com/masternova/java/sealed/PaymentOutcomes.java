package com.masternova.java.sealed;

import com.masternova.java.sealed.PaymentOutcome.Captured;
import com.masternova.java.sealed.PaymentOutcome.Failed;
import com.masternova.java.sealed.PaymentOutcome.Pending;
import com.masternova.java.valueobject.Money;
import java.time.Instant;

/**
 * Operations over {@link PaymentOutcome}, written with pattern-matching {@code switch}.
 *
 * <p>Notice: no {@code default} branch anywhere. Add a fourth outcome (say {@code Refunded}) and
 * every switch below stops COMPILING until it handles the new case — the compiler finds the
 * places you forgot. That is the main reason to use a sealed hierarchy.
 */
public final class PaymentOutcomes {

  private PaymentOutcomes() {} // ⭐ utility class: private constructor, nobody instantiates it

  /** The message shown to the learner on the checkout result page. */
  public static String message(PaymentOutcome outcome) {
    // ⭐ A switch EXPRESSION (`return switch …`, arrows, no `break`) that produces a value.
    return switch (outcome) {
      // ⭐ NESTED RECORD PATTERN + GUARD:
      //    deconstructs Captured, then deconstructs the Money inside it, then tests a condition.
      //    `_` = "I don't need this component" (unnamed pattern, Java 22+).
      case Captured(_, Money(long minor, _)) when minor == 0 -> "You're enrolled — this one was free!";
      // ⭐ TYPE PATTERN: matches any Captured and binds it to `c`.
      //    Must come AFTER the guarded case above — otherwise it would "dominate" it and the
      //    compiler rejects the code (the guarded case could never be reached).
      case Captured c -> "Payment successful: " + c.amount().display();
      case Failed(String code, boolean retryable) when retryable ->
          "Payment didn't go through (" + code + "). Please try again.";
      case Failed(String code, _) -> "Payment declined (" + code + "). Try another payment method.";
      case Pending _ -> "Waiting for your bank to confirm…";
    };
  }

  /** Whether checkout should retry automatically, given how many attempts were already made. */
  public static boolean shouldRetry(PaymentOutcome outcome, int attemptsSoFar) {
    return switch (outcome) {
      case Failed f when f.retryable() && attemptsSoFar < 3 -> true;
      // ⭐ Several patterns, one arm: allowed when the patterns bind no variables.
      case Failed _, Captured _ -> false;
      case Pending _ -> false; // pending payments are confirmed by webhook, never retried
    };
  }

  /** Short label for logs and metrics. Handles {@code null} explicitly. */
  public static String statusLabel(PaymentOutcome outcome) {
    return switch (outcome) {
      // ⭐ `case null`: without it, switching on null throws NullPointerException (as switch
      //    always has). With it, null becomes just another case.
      case null -> "UNKNOWN";
      case Captured _ -> "CAPTURED";
      case Failed _ -> "FAILED";
      case Pending _ -> "PENDING";
    };
  }

  /** True if the outcome is a pending payment whose approval window has passed. */
  public static boolean isExpired(PaymentOutcome outcome, Instant now) {
    // ⭐ PATTERN MATCHING FOR instanceof (Java 16+): test + cast + bind in one step.
    //    Old way: if (outcome instanceof Pending) { Pending p = (Pending) outcome; ... }
    //    The binding `p` is only in scope where the test is known to be true (flow scoping),
    //    which is why it can be used after `&&`.
    return outcome instanceof Pending p && now.isAfter(p.expiresAt());
  }
}
