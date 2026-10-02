package com.masternova.java.exceptions;

import java.util.Objects;

/**
 * Root of Masternova's domain exceptions — a prototype of the Phase 2 error model.
 *
 * <p>Study note: {@code patterns/java/05-exceptions-and-optional.md}.
 *
 * <ul>
 *   <li>⭐ extends {@link RuntimeException}: UNCHECKED — callers aren't forced to catch it, and
 *       Spring's {@code @Transactional} rolls back on it by default (it does NOT roll back on
 *       checked exceptions).
 *   <li>⭐ {@code sealed}: the set of error kinds is closed, so the mapping to HTTP status ({@link
 *       ProblemMapper}) is an exhaustive switch — a new kind can't silently become a 500.
 *   <li>⭐ a stable machine-readable {@link #code()} — clients branch on it, never on the message.
 * </ul>
 */
public abstract sealed class MasternovaException extends RuntimeException
    permits NotFoundException, VersionConflictException, ValidationException, RuleViolationException {

  private final String code;

  protected MasternovaException(String code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
  }

  /** ⭐ Keep the cause: the original stack trace is how someone debugs this at 2 a.m. */
  protected MasternovaException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  public String code() {
    return code;
  }
}
