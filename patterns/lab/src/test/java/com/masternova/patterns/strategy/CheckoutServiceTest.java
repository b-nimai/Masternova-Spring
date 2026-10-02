package com.masternova.patterns.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

  private final CheckoutService checkout =
      new CheckoutService(List.of(new RazorpayGateway(), new StripeGateway()));

  @Test
  void theCallerPicksTheStrategyAtRuntime() {
    ChargeRequest request = new ChargeRequest("ord_42", 49_900, "INR");

    assertThat(checkout.pay(PaymentProvider.RAZORPAY, request))
        .isEqualTo("Paid — reference pay_ord_42");
    assertThat(checkout.pay(PaymentProvider.STRIPE, request))
        .isEqualTo("Paid — reference ch_ord_42");
  }

  @Test
  void eachStrategyKeepsItsOwnRules() {
    assertThat(checkout.pay(PaymentProvider.RAZORPAY, new ChargeRequest("o1", 100, "USD")))
        .startsWith("Payment declined");
    assertThat(checkout.pay(PaymentProvider.STRIPE, new ChargeRequest("o2", 2_000_000, "INR")))
        .startsWith("Payment declined");
  }

  @Test
  void aNewStrategyNeedsNoChangeToTheContext() {
    // Open/Closed: new behaviour for RAZORPAY, plugged in without touching CheckoutService.
    PaymentGateway alwaysDecline =
        new PaymentGateway() {
          @Override
          public PaymentProvider provider() {
            return PaymentProvider.RAZORPAY;
          }

          @Override
          public PaymentResult charge(ChargeRequest request) {
            return new PaymentResult.Declined("maintenance");
          }
        };

    CheckoutService service = new CheckoutService(List.of(alwaysDecline));

    assertThat(service.pay(PaymentProvider.RAZORPAY, new ChargeRequest("o3", 100, "INR")))
        .isEqualTo("Payment declined: maintenance");
  }

  @Test
  void duplicateOrMissingStrategiesFailFast() {
    assertThatThrownBy(() -> new CheckoutService(List.of(new StripeGateway(), new StripeGateway())))
        .isInstanceOf(IllegalArgumentException.class);
    CheckoutService empty = new CheckoutService(List.of());
    assertThatThrownBy(() -> empty.pay(PaymentProvider.STRIPE, new ChargeRequest("o", 1, "INR")))
        .hasMessageContaining("no gateway");
  }

  @Test
  void invalidMoneyIsRejectedByTheRecord() {
    assertThatThrownBy(() -> new ChargeRequest("o", 0, "INR"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
