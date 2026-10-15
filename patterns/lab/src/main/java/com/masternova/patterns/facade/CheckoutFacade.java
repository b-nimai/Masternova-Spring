package com.masternova.patterns.facade;

import java.util.ArrayList;
import java.util.List;

/**
 * The second Facade (Phase 9.5, simplified from {@code
 * com.masternova.api.commerce.application.CheckoutService}): one method over pricing, the order
 * store and the payment provider. ⭐ What it owns is the SEQUENCE and where the transactions are:
 * the provider is called BETWEEN two short transactions, never inside one. The log records each
 * step so the test can check the order and that nothing ran inside a transaction it shouldn't.
 */
public final class CheckoutFacade {

  public interface Prices {
    long total(List<String> courseIds);
  }

  public interface Provider {
    String openOrder(String orderId, long amountMinor);
  }

  public final List<String> log = new ArrayList<>();
  private final Prices prices;
  private final Provider provider;
  private int nextId = 1;

  public CheckoutFacade(Prices prices, Provider provider) {
    this.prices = prices;
    this.provider = provider;
  }

  public record Placed(String orderId, String status, String providerOrderId) {}

  public Placed checkout(List<String> courseIds) {
    long total = prices.total(courseIds);
    String orderId = "o-" + nextId++;
    if (total == 0) {
      log.add("tx: place PAID + grant"); // one transaction, no provider
      return new Placed(orderId, "PAID", null);
    }
    log.add("tx1: place PENDING");
    String providerOrderId;
    try {
      log.add("provider: open order (no transaction)");
      providerOrderId = provider.openOrder(orderId, total);
    } catch (RuntimeException down) {
      log.add("tx: mark FAILED");
      throw down;
    }
    log.add("tx2: attach provider order");
    return new Placed(orderId, "PENDING", providerOrderId);
  }
}
