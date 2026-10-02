package com.masternova.api.platform;

import java.util.Map;

/**
 * 401 — the caller isn't (or is no longer) authenticated: wrong credentials, an expired or revoked
 * session. The code tells the client what to do next (log in again vs. just refresh).
 */
public final class UnauthenticatedException extends DomainException {

  public UnauthenticatedException(String code, String message) {
    super(code, message, Map.of());
  }
}
