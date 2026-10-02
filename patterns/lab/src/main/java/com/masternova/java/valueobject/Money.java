package com.masternova.java.valueobject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * An amount of money: a <b>Value Object</b>.
 *
 * <p>Study note: {@code patterns/java/01-records-and-value-objects.md}. Lines marked ⭐ are the
 * ones to remember.
 *
 * <ul>
 *   <li>Stored as a {@code long} count of <b>minor units</b> (paise, cents) — never a {@code
 *       double}, because binary floating point can't represent 0.10 exactly.
 *   <li>Immutable: every operation returns a new {@code Money}.
 *   <li>Compared by value: {@code Money.of(100, "INR").equals(Money.of(100, "INR"))} is true.
 * </ul>
 */
// ⭐ A record: the compiler generates private final fields, the constructor, the accessors
//    amountMinor() and currency(), and equals/hashCode/toString over BOTH components.
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

  // ⭐ Compact constructor (no parameter list). It runs on EVERY creation path — `new`, the
  //    factories below, deserialization — so an invalid Money can never exist. Every method
  //    below can trust `amountMinor >= 0` and `currency != null` without re-checking.
  public Money {
    Objects.requireNonNull(currency, "currency");
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amount cannot be negative: " + amountMinor);
    }
    // the fields are assigned automatically after this block
  }

  // ---------------------------------------------------------------- factories

  // ⭐ Static factory: a readable name, and it hides the Currency lookup from callers.
  //    Currency.getInstance throws IllegalArgumentException for an unknown code — no need to
  //    wrap it.
  public static Money of(long amountMinor, String currencyCode) {
    return new Money(amountMinor, Currency.getInstance(currencyCode));
  }

  public static Money zero(String currencyCode) {
    return of(0, currencyCode);
  }

  /**
   * Parses a major-unit amount: {@code parse("1499.5", "INR")} is 149950 paise.
   *
   * <p>Rejects text that isn't a number, negative amounts, and more decimals than the currency
   * has ({@code "1.005"} INR would be half a paisa).
   */
  public static Money parse(String major, String currencyCode) {
    Currency currency = Currency.getInstance(currencyCode);
    // ⭐ BigDecimal from a STRING is exact. new BigDecimal(0.1) (from a double) is not:
    //    it gives 0.1000000000000000055511151231257827...
    //    A malformed string throws NumberFormatException — which IS an IllegalArgumentException.
    BigDecimal amount = new BigDecimal(major);
    try {
      // 1499.5 -> 149950.0 : shift the decimal point by the currency's minor-unit digits
      long minor = amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
      return new Money(minor, currency); // the compact constructor still rejects negatives
    } catch (ArithmeticException tooPrecise) {
      // longValueExact() throws if a fraction is left over (1.005 INR -> 100.5 paise)
      throw new IllegalArgumentException(
          "more decimals than " + currencyCode + " allows: " + major, tooPrecise);
    }
  }

  // ---------------------------------------------------------------- arithmetic

  // ⭐ Every operation RETURNS A NEW Money — `this` never changes. That is what makes it safe to
  //    share a Money between threads, cache it, or use it as a HashMap key.
  public Money plus(Money other) {
    requireSameCurrency(other);
    // ⭐ Math.addExact throws ArithmeticException on overflow instead of silently wrapping to a
    //    negative number (Long.MAX_VALUE + 1 == Long.MIN_VALUE with plain `+`).
    return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    // No overflow possible here (both operands are >= 0); a negative result is rejected by the
    // compact constructor — the invariant does the work.
    return new Money(amountMinor - other.amountMinor, currency);
  }

  public Money times(int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity cannot be negative: " + quantity);
    }
    return new Money(Math.multiplyExact(amountMinor, quantity), currency);
  }

  /** Price after a percentage coupon; the discount is rounded half-up to the minor unit. */
  public Money percentOff(int percent) {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("percent must be 0..100, was " + percent);
    }
    // ⭐ Integer half-up rounding without floating point: (x + 50) / 100.
    //    999 paise × 15% = 149.85 paise → (14985 + 50) / 100 = 150 → result 849.
    long discount = (Math.multiplyExact(amountMinor, percent) + 50) / 100;
    return new Money(amountMinor - discount, currency);
  }

  /**
   * Splits into {@code parts} shares that add up EXACTLY to this amount. ₹10.00 into 3 is [334,
   * 333, 333]: the remainder goes to the first shares, one minor unit each.
   */
  public List<Money> allocate(int parts) {
    if (parts < 1) {
      throw new IllegalArgumentException("parts must be at least 1, was " + parts);
    }
    // ⭐ Fowler's allocation: integer division, then hand out the remainder one unit at a time.
    //    Naively 1000 / 3 = 333 and 333 × 3 = 999 — a paisa vanishes.
    long base = amountMinor / parts;
    long remainder = amountMinor % parts;

    List<Money> shares = new ArrayList<>(parts);
    for (int i = 0; i < parts; i++) {
      long share = i < remainder ? base + 1 : base;
      shares.add(new Money(share, currency));
    }
    // ⭐ List.copyOf returns an UNMODIFIABLE list: callers can't add/remove shares and break the
    //    "adds up exactly" guarantee. (Records/value objects should hand out immutable data.)
    return List.copyOf(shares);
  }

  public boolean isZero() {
    return amountMinor == 0;
  }

  // ---------------------------------------------------------------- ordering & display

  // ⭐ Comparable gives Money a NATURAL ORDER, so stream().sorted(), Collections.max and TreeMap
  //    work. Long.compare avoids the classic bug `return (int) (a - b)` (overflow + truncation).
  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return Long.compare(amountMinor, other.amountMinor);
  }

  /** {@code "INR 1499.00"}, {@code "JPY 500"} — for logs; real UIs format with a Locale. */
  public String display() {
    // BigDecimal.valueOf(unscaled, scale): 149900 with scale 2 is 1499.00 — exact, no rounding.
    BigDecimal major = BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits());
    return currency.getCurrencyCode() + " " + major.toPlainString();
  }

  // equals(), hashCode() and toString() are generated by the record. They are correct for a value
  // object because BOTH components (a primitive long and an immutable Currency) are values.

  private void requireSameCurrency(Money other) {
    // ⭐ ₹100 + $100 is a bug, not ₹200 — refuse it loudly.
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "currency mismatch: " + currency + " vs " + other.currency);
    }
  }
}
