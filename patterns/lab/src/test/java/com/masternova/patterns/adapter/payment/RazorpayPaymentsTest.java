package com.masternova.patterns.adapter.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RazorpayPaymentsTest {

  @Test
  void translatesTheRequestAndTheAnswer() {
    Payments payments =
        new RazorpayPayments(
            (path, body) -> {
              assertThat(path).isEqualTo("/v1/orders");
              assertThat(body).containsEntry("amount", 49_900L).containsEntry("receipt", "o-1");
              return Map.of("id", "order_N1", "status", "created");
            });

    assertThat(payments.openOrder("o-1", 49_900, "INR")).isEqualTo("order_N1");
  }

  @Test
  void translatesTheFailures() {
    assertThat(failure(new VendorHttp.HttpError(503, "down")).temporary).isTrue();
    assertThat(failure(new VendorHttp.HttpError(429, "slow down")).temporary).isTrue();
    assertThat(failure(new VendorHttp.NoAnswer("timed out")).temporary).isTrue();
    assertThat(failure(new VendorHttp.HttpError(400, "bad amount")).temporary).isFalse();
  }

  private static Payments.ProviderFailed failure(RuntimeException vendorError) {
    Payments payments =
        new RazorpayPayments(
            (path, body) -> {
              throw vendorError;
            });
    try {
      payments.openOrder("o-1", 1, "INR");
    } catch (Payments.ProviderFailed e) {
      return e;
    }
    throw new AssertionError("expected a ProviderFailed");
  }

  @Test
  void anAnswerWithoutAnIdIsAFailure() {
    assertThatThrownBy(() -> new RazorpayPayments((p, b) -> Map.of()).openOrder("o", 1, "INR"))
        .isInstanceOf(Payments.ProviderFailed.class);
  }
}
