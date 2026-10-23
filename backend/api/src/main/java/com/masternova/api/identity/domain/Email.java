package com.masternova.api.identity.domain;

import com.masternova.api.platform.ValidationException;
import java.util.Locale;

/**
 * An email address as a VALUE OBJECT (note 01): normalised once (trimmed, lower-cased) so "
 * Asha@Example.com " and "asha@example.com" are the same account, and validated so an invalid one
 * can't exist.
 */
public record Email(String value) {

  private static final int MAX_LENGTH = 320;

  /** Java's {@code \s}: an address has none of these. */
  private static final String WHITESPACE = " \t\n\u000B\f\r";

  public Email {
    if (value == null) {
      throw ValidationException.of("email", "NotNull", "Email is required.");
    }
    value = value.strip().toLowerCase(Locale.ROOT); // ⭐ normalise in the compact constructor
    if (value.length() > MAX_LENGTH || !hasAddressShape(value)) {
      throw ValidationException.of("email", "Email", "Enter a valid email address.");
    }
  }

  /**
   * Deliberately simple: one @, something on both sides, a dot inside the domain. Real proof of an
   * address is the verification email. ⭐ A scan, not a regex: {@code [^@]+\.[^@]+} backtracks
   * polynomially on a crafted domain (CodeQL java/polynomial-redos), and this runs on user input.
   */
  private static boolean hasAddressShape(String value) {
    int at = value.indexOf('@');
    if (at <= 0 || value.indexOf('@', at + 1) != -1) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (WHITESPACE.indexOf(value.charAt(i)) >= 0) {
        return false;
      }
    }
    int dot = value.indexOf('.', at + 2); // at least one character between @ and the dot
    return dot != -1 && dot < value.length() - 1;
  }

  @Override
  public String toString() {
    return value;
  }
}
