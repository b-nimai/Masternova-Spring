package com.masternova.api.commerce.infrastructure;

import com.masternova.api.commerce.application.CommerceProperties;
import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.platform.MasternovaProperties;
import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class CommerceConfig {

  private static final Logger log = LoggerFactory.getLogger(CommerceConfig.class);

  /** The provider calls the webhook with no token: its signature over the raw body is the auth. */
  @Bean
  PublicEndpoints commercePublicEndpoints() {
    return () -> List.of(new Endpoint(HttpMethod.POST, "/api/v1/webhooks/payments"));
  }

  /**
   * ⭐ The Strategy is picked HERE, once, by configuration: exactly one of these two beans exists,
   * and everything else injects {@link PaymentGateway}.
   */
  @Bean
  @ConditionalOnProperty(name = "masternova.commerce.provider", havingValue = "razorpay")
  PaymentGateway razorpayGateway(
      CommerceProperties props, MasternovaProperties platform, JsonMapper json) {
    return razorpay(props.razorpay(), platform.checkout().paymentTimeout(), json);
  }

  /** Its concrete type is the bean type, so the dev checkout simulator can inject it. */
  @Bean
  @ConditionalOnProperty(name = "masternova.commerce.provider", havingValue = "fake")
  FakePaymentGateway fakePaymentGateway(CommerceProperties props, JsonMapper json) {
    log.warn("FAKE payment provider active: payments are simulated. Never in production.");
    return new FakePaymentGateway(props.fake().keySecret(), props.fake().webhookSecret(), json);
  }

  static RazorpayGateway razorpay(
      CommerceProperties.Razorpay keys, Duration timeout, JsonMapper json) {
    if (keys.keyId().isBlank() || keys.keySecret().isBlank() || keys.webhookSecret().isBlank()) {
      throw new IllegalStateException(
          "masternova.commerce.provider=RAZORPAY needs RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and"
              + " RAZORPAY_WEBHOOK_SECRET");
    }
    // ⭐ a slow provider must not hold a request thread forever: connect AND read time out
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    requests.setReadTimeout(timeout);
    String basic =
        Base64.getEncoder()
            .encodeToString(
                (keys.keyId() + ":" + keys.keySecret()).getBytes(StandardCharsets.UTF_8));
    RestClient http =
        RestClient.builder()
            .baseUrl(keys.baseUrl().toString())
            .requestFactory(requests)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
            .build();
    return new RazorpayGateway(
        http, keys.keyId(), new RazorpaySignatures(keys.keySecret(), keys.webhookSecret()), json);
  }
}
