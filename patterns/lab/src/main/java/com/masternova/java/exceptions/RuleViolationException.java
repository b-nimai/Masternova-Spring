package com.masternova.java.exceptions;

/**
 * A well-formed request that breaks a business rule ("coupon expired", "course not published") —
 * the code is specific to the rule.
 */
public final class RuleViolationException extends MasternovaException {

  public RuleViolationException(String code, String message) {
    super(code, message);
  }
}
