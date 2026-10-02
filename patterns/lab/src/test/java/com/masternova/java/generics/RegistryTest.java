package com.masternova.java.generics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegistryTest {

  enum Provider {
    RAZORPAY,
    STRIPE
  }

  interface Gateway extends Keyed<Provider> {
    String charge(long amountMinor);
  }

  record Razorpay() implements Gateway {
    public Provider key() {
      return Provider.RAZORPAY;
    }

    public String charge(long amountMinor) {
      return "pay_" + amountMinor;
    }
  }

  record Stripe() implements Gateway {
    public Provider key() {
      return Provider.STRIPE;
    }

    public String charge(long amountMinor) {
      return "ch_" + amountMinor;
    }
  }

  @Test
  void looksUpByTheKeyEachValueReports() {
    Registry<Provider, Gateway> gateways = Registry.of(List.of(new Razorpay(), new Stripe()));

    assertThat(gateways.get(Provider.STRIPE).charge(100)).isEqualTo("ch_100");
    assertThat(gateways.keys()).containsExactly(Provider.RAZORPAY, Provider.STRIPE);
  }

  @Test
  void acceptsAListOfASubtype() {
    // List<Razorpay> is NOT a List<Gateway> (invariance) — but Collection<? extends V> takes it.
    List<Razorpay> onlyRazorpay = List.of(new Razorpay());

    Registry<Provider, Gateway> gateways = Registry.of(onlyRazorpay);

    assertThat(gateways.find(Provider.STRIPE)).isEmpty();
    assertThat(gateways.find(Provider.RAZORPAY)).isPresent();
  }

  @Test
  void duplicateKeysAndMissingKeysFailLoudly() {
    assertThatThrownBy(() -> Registry.of(List.of(new Stripe(), new Stripe())))
        .isInstanceOf(IllegalArgumentException.class);

    Registry<Provider, Gateway> empty = Registry.of(List.<Gateway>of());
    assertThatThrownBy(() -> empty.get(Provider.RAZORPAY))
        .hasMessageContaining("nothing registered");
  }
}
