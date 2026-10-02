package com.masternova.api.platform;

import java.util.Map;

/**
 * 422 — well-formed and permitted, but a business rule says no: "coupon expired", "course not ready
 * to publish". The code is specific to the rule. "You are not finished yet — fix it."
 */
public final class RuleViolationException extends DomainException {

  public RuleViolationException(String code, String message, Map<String, Object> details) {
    super(code, message, details);
  }

  public RuleViolationException(String code, String message) {
    this(code, message, Map.of());
  }
}
