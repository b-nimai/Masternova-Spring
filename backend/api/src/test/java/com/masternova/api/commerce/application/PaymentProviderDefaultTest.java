package com.masternova.api.commerce.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * Review fix: the simulator was the DEFAULT provider, so a deployment that forgot PAYMENT_PROVIDER
 * handed out courses for free. The shipped default must fail CLOSED (Razorpay, which refuses to
 * start without its secrets); only an explicit {@code fake} turns the simulator on.
 */
class PaymentProviderDefaultTest {

  @Test
  void theShippedDefaultIsRazorpay() throws Exception {
    var sources =
        new YamlPropertySourceLoader()
            .load("main", new ClassPathResource("application.yaml")); // main, not test config/
    assertThat(sources.getFirst().getProperty("masternova.commerce.provider"))
        .hasToString("${PAYMENT_PROVIDER:razorpay}");
  }
}
