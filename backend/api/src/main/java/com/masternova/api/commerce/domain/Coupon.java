package com.masternova.api.commerce.domain;

import com.masternova.kernel.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A discount code an admin creates (LLD §3). The row says WHICH {@link Discount} strategy it is and
 * WHEN it applies; the rules that read the "when" are {@link CouponEligibility}'s.
 *
 * <p>Redemptions are counted when an order is PAID ({@code CouponRepository.redeem}), never here:
 * the count is a database increment, not a read-modify-write of this entity.
 */
@Entity
@Table(name = "coupon")
public class Coupon {

  /** The two kinds, each a {@link Discount} strategy. Stored as text (a CHECK lists them). */
  public enum Kind {
    PERCENT,
    AMOUNT
  }

  private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{3,40}");

  @Id private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Kind kind;

  @Column(name = "percent_off")
  private Integer percentOff;

  @Column(name = "amount_off_minor")
  private Long amountOffMinor;

  @Column(name = "max_discount_minor")
  private Long maxDiscountMinor;

  @Column(nullable = false, length = 3)
  private Currency currency;

  @Column(name = "min_subtotal_minor", nullable = false)
  private long minSubtotalMinor;

  @Column(name = "course_id")
  private UUID courseId;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "max_redemptions")
  private Integer maxRedemptions;

  @Column(nullable = false, insertable = false, updatable = false)
  private int redemptions;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version
  private Long version; // null until saved: how Spring Data tells a new coupon (assigned id)

  protected Coupon() {} // JPA

  /** What an admin may set; the factory checks it the way the table's CHECKs would. */
  public record Terms(
      Optional<Integer> percentOff,
      Optional<Money> amountOff,
      Optional<Money> maxDiscount,
      Money minSubtotal,
      Optional<UUID> courseId,
      Optional<Instant> startsAt,
      Optional<Instant> endsAt,
      Optional<Integer> maxRedemptions) {}

  public static Coupon create(String code, Terms terms, Currency currency, Instant now) {
    Coupon c = new Coupon();
    c.code = normalize(code);
    if (!CODE.matcher(c.code).matches()) {
      throw new IllegalArgumentException("a code is 3–40 of A–Z, 0–9, _ and -: " + code);
    }
    if (terms.percentOff().isPresent() == terms.amountOff().isPresent()) {
      throw new IllegalArgumentException("a coupon is either a percentage or an amount off");
    }
    c.kind = terms.percentOff().isPresent() ? Kind.PERCENT : Kind.AMOUNT;
    if (c.kind == Kind.AMOUNT && terms.maxDiscount().isPresent()) { // V18's coupon_kind_ck
      throw new IllegalArgumentException("only a percentage coupon has a cap");
    }
    c.currency = Objects.requireNonNull(currency, "currency");
    c.percentOff = terms.percentOff().orElse(null);
    c.amountOffMinor = terms.amountOff().map(m -> inCurrency(m, currency)).orElse(null);
    c.maxDiscountMinor = terms.maxDiscount().map(m -> inCurrency(m, currency)).orElse(null);
    c.minSubtotalMinor = inCurrency(terms.minSubtotal(), currency);
    c.courseId = terms.courseId().orElse(null);
    c.startsAt = terms.startsAt().orElse(null);
    c.endsAt = terms.endsAt().orElse(null);
    c.maxRedemptions = terms.maxRedemptions().orElse(null);
    if (c.startsAt != null && c.endsAt != null && !c.endsAt.isAfter(c.startsAt)) {
      throw new IllegalArgumentException("a coupon must end after it starts");
    }
    if (c.maxRedemptions != null && c.maxRedemptions < 1) {
      throw new IllegalArgumentException("a redemption limit must be at least 1");
    }
    c.active = true;
    c.createdAt = now;
    c.discount(); // ⭐ builds the strategy once: an invalid percent or amount fails here, not later
    return c;
  }

  /** Codes are case-insensitive for people and upper-case in the table. */
  public static String normalize(String code) {
    return code.strip().toUpperCase(Locale.ROOT);
  }

  /** ⭐ The ONE place a row becomes a strategy — exhaustive over the sealed kinds. */
  public Discount discount() {
    return switch (kind) {
      case PERCENT ->
          new Discount.PercentOff(
              percentOff, Optional.ofNullable(maxDiscountMinor).map(this::money));
      case AMOUNT -> new Discount.AmountOff(money(amountOffMinor));
    };
  }

  public void deactivate() {
    active = false;
  }

  private Money money(long minor) {
    return new Money(minor, currency);
  }

  private static long inCurrency(Money m, Currency currency) {
    if (!m.currency().equals(currency)) {
      throw new IllegalArgumentException("a coupon's amounts are in " + currency);
    }
    return m.amountMinor();
  }

  public String code() {
    return code;
  }

  public Kind kind() {
    return kind;
  }

  public Optional<Integer> percentOff() {
    return Optional.ofNullable(percentOff);
  }

  public Optional<Money> amountOff() {
    return Optional.ofNullable(amountOffMinor).map(this::money);
  }

  public Optional<Money> maxDiscount() {
    return Optional.ofNullable(maxDiscountMinor).map(this::money);
  }

  public Currency currency() {
    return currency;
  }

  public Money minSubtotal() {
    return money(minSubtotalMinor);
  }

  public Optional<UUID> courseId() {
    return Optional.ofNullable(courseId);
  }

  public Optional<Instant> startsAt() {
    return Optional.ofNullable(startsAt);
  }

  public Optional<Instant> endsAt() {
    return Optional.ofNullable(endsAt);
  }

  public Optional<Integer> maxRedemptions() {
    return Optional.ofNullable(maxRedemptions);
  }

  /** As loaded; the database counts (a redemption never goes through this entity). */
  public int redemptions() {
    return redemptions;
  }

  public boolean isActive() {
    return active;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
