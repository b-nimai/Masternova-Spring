package com.masternova.patterns.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutFacadeTest {

  @Test
  void theProviderIsCalledBetweenTwoTransactions() {
    CheckoutFacade facade = new CheckoutFacade(ids -> 49_900, (id, amount) -> "order_N1");

    var placed = facade.checkout(List.of("java"));

    assertThat(placed.providerOrderId()).isEqualTo("order_N1");
    assertThat(facade.log)
        .containsExactly(
            "tx1: place PENDING", "provider: open order (no transaction)", "tx2: attach provider order");
  }

  @Test
  void aFreeOrderNeverReachesTheProvider() {
    CheckoutFacade facade =
        new CheckoutFacade(
            ids -> 0,
            (id, amount) -> {
              throw new AssertionError("no provider for a free order");
            });

    assertThat(facade.checkout(List.of("java")).status()).isEqualTo("PAID");
  }

  @Test
  void aProviderFailureLeavesAFailedOrder() {
    CheckoutFacade facade =
        new CheckoutFacade(
            ids -> 1,
            (id, amount) -> {
              throw new IllegalStateException("timed out");
            });

    assertThatThrownBy(() -> facade.checkout(List.of("java"))).hasMessage("timed out");
    assertThat(facade.log).endsWith("tx: mark FAILED");
  }
}
