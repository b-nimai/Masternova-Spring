package com.masternova.api.platform;

import java.util.Map;

/**
 * 403 — signed in, not permitted. Carries a machine-readable {@code reason} (e.g. {@code
 * NO_ENTITLEMENT}) so the client can show the right next step — a buy button, a support link (API
 * conventions §10).
 */
public final class ForbiddenException extends DomainException {

  public ForbiddenException(String reason, String message) {
    super("FORBIDDEN", message, Map.of("reason", reason));
  }
}
