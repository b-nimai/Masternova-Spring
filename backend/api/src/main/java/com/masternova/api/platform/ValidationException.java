package com.masternova.api.platform;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 400 — the input is malformed. Reports EVERY field problem at once, in the same {@code errors}
 * shape that Bean Validation failures produce, so clients handle one format.
 */
public final class ValidationException extends DomainException {

  /** One field problem: which field, which rule (a stable code), and a human sentence. */
  public record FieldError(String field, String code, String message) {
    public FieldError {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(message, "message");
    }
  }

  private final List<FieldError> errors;

  public ValidationException(List<FieldError> errors) {
    super(
        "VALIDATION_FAILED",
        "The request has invalid fields.",
        Map.of("errors", List.copyOf(errors)));
    if (errors.isEmpty()) {
      throw new IllegalArgumentException("a ValidationException needs at least one error");
    }
    this.errors = List.copyOf(errors);
  }

  public static ValidationException of(String field, String code, String message) {
    return new ValidationException(List.of(new FieldError(field, code, message)));
  }

  public List<FieldError> errors() {
    return errors;
  }
}
