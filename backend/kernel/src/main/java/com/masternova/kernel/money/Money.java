package com.masternova.kernel.money;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * An amount of money: a VALUE OBJECT in minor units (paise, cents) plus an ISO-4217 currency. The
 * production version of the Phase 1 lab class ({@code
 * patterns/java/01-records-and-value-objects.md}), shared through the kernel because catalog
 * (prices) and commerce (orders, coupons) both need it.
 *
 * <ul>
 *   <li>⭐ {@code long} minor units, never {@code double}: binary floating point can't hold 0.10.
 *   <li>⭐ Immutable and compared by value: every operation returns a new {@code Money}.
 *   <li>⭐ {@code @Embeddable}: JPA stores it as COLUMNS OF THE OWNING TABLE ({@code price_minor},
 *       {@code currency} on {@code course}), not as a table of its own. Hibernate 6.2+ builds
 *       records through their canonical constructor, so the compact constructor's checks run on
 *       every row it loads too — a corrupt row fails loudly instead of becoming a negative price.
 * </ul>
 *
 * <p>Why {@code @Embeddable} and not an {@code AttributeConverter}: a converter maps ONE attribute
 * to ONE column. Money is two columns (amount + currency), so it's an embeddable. Single-column
 * value objects (catalog's {@code LectureDuration}) use a converter. Study note: {@code
 * patterns/java/12-jpa-value-objects-and-fetching.md}.
 */
@Embeddable
@DesignPattern(
    value = Pattern.VALUE_OBJECT,
    role = "ValueObject",
    note = "patterns/java/01-records-and-value-objects.md")
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

  // ⭐ The compact constructor runs on EVERY creation path: `new`, the factories, Hibernate
  //    loading a row, Jackson. An invalid Money can't exist anywhere in the system.
  public Money {
    Objects.requireNonNull(currency, "currency");
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amount cannot be negative: " + amountMinor);
    }
  }

  public static Money of(long amountMinor, String currencyCode) {
    return new Money(amountMinor, Currency.getInstance(currencyCode));
  }

  public static Money zero(String currencyCode) {
    return of(0, currencyCode);
  }

  /**
   * Parses a major-unit amount: {@code parse("1499.5", "INR")} is 149950 paise. Rejects
   * non-numbers, negatives and more decimals than the currency has ({@code "1.005"} INR is half a
   * paisa).
   */
  public static Money parse(String major, String currencyCode) {
    Currency currency = Currency.getInstance(currencyCode);
    BigDecimal amount = new BigDecimal(major); // ⭐ from a String: exact (new BigDecimal(0.1) isn't)
    try {
      long minor = amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
      return new Money(minor, currency);
    } catch (ArithmeticException tooPrecise) {
      throw new IllegalArgumentException(
          "more decimals than " + currencyCode + " allows: " + major, tooPrecise);
    }
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amountMinor, other.amountMinor), currency); // ⭐ overflow throws
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(amountMinor - other.amountMinor, currency); // negative → constructor throws
  }

  public Money times(int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity cannot be negative: " + quantity);
    }
    return new Money(Math.multiplyExact(amountMinor, quantity), currency);
  }

  /** Price after a percentage discount, the discount rounded half-up to the minor unit. */
  public Money percentOff(int percent) {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("percent must be 0..100, was " + percent);
    }
    long discount = (Math.multiplyExact(amountMinor, percent) + 50) / 100; // ⭐ integer half-up
    return new Money(amountMinor - discount, currency);
  }

  /**
   * Splits into {@code parts} shares that add up EXACTLY to this amount: ₹10 / 3 = [334, 333, 333].
   */
  public List<Money> allocate(int parts) {
    if (parts < 1) {
      throw new IllegalArgumentException("parts must be at least 1, was " + parts);
    }
    long base = amountMinor / parts;
    long remainder = amountMinor % parts;
    List<Money> shares = new ArrayList<>(parts);
    for (int i = 0; i < parts; i++) {
      shares.add(new Money(i < remainder ? base + 1 : base, currency));
    }
    return List.copyOf(shares);
  }

  public boolean isZero() {
    return amountMinor == 0;
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return Long.compare(amountMinor, other.amountMinor);
  }

  /** {@code "INR 1499.00"} — for logs and emails; UIs format with the viewer's locale. */
  public String display() {
    BigDecimal major = BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits());
    return currency.getCurrencyCode() + " " + major.toPlainString();
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) { // ⭐ ₹100 + $100 is a bug, not ₹200
      throw new IllegalArgumentException(
          "currency mismatch: " + currency + " vs " + other.currency);
    }
  }
}
