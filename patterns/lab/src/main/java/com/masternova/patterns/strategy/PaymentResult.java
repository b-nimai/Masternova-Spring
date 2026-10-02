package com.masternova.patterns.strategy;

/**
 * Every strategy returns one of exactly two outcomes.
 *
 * <p>Java: {@code sealed} closes the hierarchy, so a {@code switch} over it is checked for
 * exhaustiveness by the compiler — add a third outcome and every unhandled switch stops compiling.
 */
public sealed interface PaymentResult {

  record Approved(String providerReference) implements PaymentResult {}

  record Declined(String reason) implements PaymentResult {}
}
