package com.masternova.api.commerce.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.commerce.domain.Coupon;
import com.masternova.api.commerce.domain.CouponRepository;
import com.masternova.api.commerce.domain.Pricer;
import com.masternova.api.commerce.domain.Quote;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.platform.MasternovaProperties;
import com.masternova.api.platform.RuleViolationException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Prices a cart for a buyer: fetches what the pure {@link Pricer} needs — catalog's offers (one
 * statement), the courses the buyer already holds (entitlement, cached) and the coupon — then lets
 * it decide. The cart page asks this on every change; checkout asks it again before charging.
 */
@Service
public class PricingService {

  private final CatalogApi catalog;
  private final EntitlementApi entitlements;
  private final CouponRepository coupons;
  private final int cartMaxItems;
  private final Clock clock;

  PricingService(
      CatalogApi catalog,
      EntitlementApi entitlements,
      CouponRepository coupons,
      MasternovaProperties properties,
      Clock clock) {
    this.catalog = catalog;
    this.entitlements = entitlements;
    this.coupons = coupons;
    this.cartMaxItems = properties.checkout().cartMaxItems();
    this.clock = clock;
  }

  // ⭐ no transaction: catalog's read and the coupon's are short ones of their own, and no pooled
  //    connection is held across entitlement's Redis lookups
  public Quote quote(UUID buyerId, List<UUID> courseIds, Optional<String> couponCode) {
    Pricer.requireCartShape(courseIds, cartMaxItems);
    Optional<Coupon> coupon = couponCode.filter(c -> !c.isBlank()).map(this::coupon);
    Pricer.Cart cart =
        new Pricer.Cart(
            buyerId,
            courseIds,
            catalog.offers(courseIds),
            entitlements.heldCourses(buyerId, courseIds));
    return Pricer.price(cart, coupon, clock.instant());
  }

  /** ⭐ An unknown code is a 422 like any other bad code, not a 404 that confirms it's unknown. */
  private Coupon coupon(String code) {
    return coupons
        .findById(Coupon.normalize(code))
        .orElseThrow(
            () -> new RuleViolationException("COUPON_NOT_FOUND", "There's no such coupon."));
  }
}
