package com.masternova.api.platform;

import java.util.Map;

/**
 * 409 — the resource's STATE forbids the request: a concurrent edit, an illegal state transition, a
 * duplicate. "You are racing someone, or the state is wrong — reload."
 */
public final class ConflictException extends DomainException {

  public ConflictException(String code, String message, Map<String, Object> details) {
    super(code, message, details);
  }

  public ConflictException(String code, String message) {
    this(code, message, Map.of());
  }

  /**
   * Optimistic-locking clash: the client edited {@code expected}, the stored version is {@code
   * actual}.
   */
  public static ConflictException versionConflict(long expected, long actual) {
    return new ConflictException(
        "VERSION_CONFLICT",
        "This was changed elsewhere. Reload and try again.",
        Map.of("expectedVersion", expected, "currentVersion", actual));
  }
}
