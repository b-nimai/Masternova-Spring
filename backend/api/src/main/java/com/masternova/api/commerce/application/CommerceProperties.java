package com.masternova.api.commerce.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Commerce settings, bound from {@code masternova.commerce.*}. The cart limit and the provider's
 * timeout are in {@code masternova.checkout.*} (MasternovaProperties), where Phase 1 put them.
 *
 * @param provider which {@code PaymentGateway} runs (LLD §6)
 * @param razorpay Razorpay's keys (test or live mode): required when {@code provider} is RAZORPAY
 * @param fake the network-free double's signing secrets (dev-only defaults)
 */
@Validated
@ConfigurationProperties(prefix = "masternova.commerce")
public record CommerceProperties(
    @NotNull Provider provider,
    @Valid @DefaultValue Razorpay razorpay,
    @Valid @DefaultValue Fake fake) {

  public enum Provider {
    RAZORPAY,
    /** A Razorpay double with no network: dev, e2e and CI. ⚠️ Never in production. */
    FAKE
  }

  /**
   * @param keyId public: the browser's checkout widget gets it
   * @param keySecret signs the browser's callback and authenticates our API calls
   * @param webhookSecret signs webhooks (set in Razorpay's dashboard, separate from the key)
   */
  public record Razorpay(
      @DefaultValue("") String keyId,
      @DefaultValue("") String keySecret,
      @DefaultValue("") String webhookSecret,
      @DefaultValue("https://api.razorpay.com") @NotNull URI baseUrl) {}

  public record Fake(
      @DefaultValue("dev-only-fake-payments-key-secret") @NotBlank String keySecret,
      @DefaultValue("dev-only-fake-payments-webhook-secret") @NotBlank String webhookSecret) {}
}
