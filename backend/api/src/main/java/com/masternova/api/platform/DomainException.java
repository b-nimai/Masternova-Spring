package com.masternova.api.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Root of every error a Masternova module raises on purpose — part of the platform module's public
 * API.
 *
 * <ul>
 *   <li>⭐ unchecked: callers aren't forced to catch it, and {@code @Transactional} rolls back on it
 *       (note 09 §5).
 *   <li>⭐ sealed: the KINDS of error are closed, so {@code GlobalExceptionHandler} maps them with
 *       an exhaustive switch — a new kind can't silently become a 500. Modules don't subclass; they
 *       pick a kind and give it a module-specific {@link #code()}.
 *   <li>⭐ {@link #code()} is the stable, machine-readable contract; the message is copy. {@link
 *       #details()} carries typed context returned to the client as extension members.
 * </ul>
 *
 * <p>Design: {@code docs/lld/platform-kernel.md}; prototype and rationale: {@code
 * patterns/java/05-exceptions-and-optional.md} §5.
 */
public abstract sealed class DomainException extends RuntimeException
    permits NotFoundException,
        ConflictException,
        ValidationException,
        RuleViolationException,
        ForbiddenException,
        UnauthenticatedException {

  private final String code;
  private final Map<String, Object> details;

  protected DomainException(String code, String message, Map<String, Object> details) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
    // insertion order kept (stable JSON), and nobody can modify it after construction
    this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
  }

  /** Stable machine-readable code, e.g. {@code VERSION_CONFLICT}, {@code COUPON_EXPIRED}. */
  public String code() {
    return code;
  }

  /** Extra context for clients, e.g. {@code expectedVersion} / {@code currentVersion}. */
  public Map<String, Object> details() {
    return details;
  }
}
