package com.masternova.java.exceptions;

import java.util.Map;

/**
 * Turns a domain exception into an RFC 9457 "problem" — the plain-Java version of what Phase 2's
 * {@code GlobalExceptionHandler} does with Spring's {@code ProblemDetail}.
 */
public final class ProblemMapper {

  /** The fields of an {@code application/problem+json} body. */
  public record Problem(
      int status, String title, String code, String detail, Map<String, Object> extras) {}

  private ProblemMapper() {}

  public static Problem toProblem(MasternovaException exception) {
    // ⭐ Exhaustive over the sealed hierarchy (note 02): a new exception type is a COMPILE
    //    error here until someone decides its status. No silent 500s.
    return switch (exception) {
      case NotFoundException e -> problem(404, "Not Found", e, Map.of("resource", e.resource()));
      case VersionConflictException e ->
          problem(409, "Conflict", e, Map.of("expectedVersion", e.expected(), "currentVersion", e.actual()));
      case ValidationException e -> problem(400, "Bad Request", e, Map.of("errors", e.errors()));
      case RuleViolationException e -> problem(422, "Unprocessable Content", e, Map.of());
    };
  }

  /** Anything that is NOT a domain exception is a bug: 500, and never leak its message. */
  public static Problem unexpected(Throwable bug) {
    return new Problem(500, "Internal Server Error", "INTERNAL", "Something went wrong.", Map.of());
  }

  private static Problem problem(
      int status, String title, MasternovaException e, Map<String, Object> extras) {
    return new Problem(status, title, e.code(), e.getMessage(), extras);
  }
}
