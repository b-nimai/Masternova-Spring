package com.masternova.java.exceptions;

import java.util.List;

/** One or more input fields are invalid — reports ALL of them at once, not just the first. */
public final class ValidationException extends MasternovaException {

  /** A single field problem, e.g. {@code ("title", "NotBlank")}. */
  public record FieldError(String field, String code) {}

  private final List<FieldError> errors;

  public ValidationException(List<FieldError> errors) {
    super("VALIDATION_FAILED", errors.size() + " invalid field(s)");
    if (errors.isEmpty()) {
      throw new IllegalArgumentException("a ValidationException needs at least one error");
    }
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}
