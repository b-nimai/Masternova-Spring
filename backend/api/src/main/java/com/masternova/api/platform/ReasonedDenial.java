package com.masternova.api.platform;

/**
 * An authorization result that says WHY it denied. A {@code @PreAuthorize} expression may return an
 * {@code AuthorizationResult} (Spring Security 6.3+); when that result also implements this, the
 * 403 carries its reason instead of the generic {@code INSUFFICIENT_ROLE} (API conventions §10).
 *
 * <p>⭐ In platform so the error handler can read it without knowing which module denied: the
 * entitlement engine implements it, platform never imports entitlement.
 */
public interface ReasonedDenial {

  /** A stable, machine-readable code, e.g. {@code NO_ENTITLEMENT}. */
  String denialReason();
}
