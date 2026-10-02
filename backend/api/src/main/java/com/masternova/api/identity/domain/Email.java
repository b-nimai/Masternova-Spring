package com.masternova.api.identity.domain;

import com.masternova.api.platform.ValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email address as a VALUE OBJECT (note 01): normalised once (trimmed, lower-cased) so "
 * Asha@Example.com " and "asha@example.com" are the same account, and validated so an invalid one
 * can't exist.
 */
public record Email(String value) {

  private static final int MAX_LENGTH = 320;
  // deliberately simple: one @, something on both sides, a dot in the domain. Real proof of an
  // address is the verification email, not a regex.
  private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public Email {
    if (value == null) {
      throw ValidationException.of("email", "NotNull", "Email is required.");
    }
    value = value.strip().toLowerCase(Locale.ROOT); // ⭐ normalise in the compact constructor
    if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
      throw ValidationException.of("email", "Email", "Enter a valid email address.");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
