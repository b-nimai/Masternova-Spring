package com.masternova.api.learning.ioc;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.platform.MasternovaProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Binding and validating the real MasternovaProperties. Study note §8. */
class PropertiesBindingLearningTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(MasternovaProperties.class)
  static class Props {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Props.class);

  @Test
  void bindsTypesAndAppliesDefaults() {
    runner
        .withPropertyValues("masternova.web-url=https://masternova.dev")
        .run(
            ctx -> {
              MasternovaProperties props = ctx.getBean(MasternovaProperties.class);
              assertThat(props.webUrl()).isEqualTo(URI.create("https://masternova.dev"));
              assertThat(props.checkout().cartMaxItems()).isEqualTo(20); //      @DefaultValue
              assertThat(props.checkout().paymentTimeout()).isEqualTo(Duration.ofSeconds(10));
            });
  }

  @Test
  void relaxedBindingAndHumanFriendlyDurations() {
    runner
        .withPropertyValues(
            "masternova.web-url=https://masternova.dev",
            "masternova.checkout.cartMaxItems=5", //          camelCase works as well as kebab-case
            "masternova.checkout.payment-timeout=1500ms") //  "1500ms", "10s", "PT10S", "2m" all
        // bind
        .run(
            ctx -> {
              MasternovaProperties props = ctx.getBean(MasternovaProperties.class);
              assertThat(props.checkout().cartMaxItems()).isEqualTo(5);
              assertThat(props.checkout().paymentTimeout()).isEqualTo(Duration.ofMillis(1500));
            });
  }

  @Test
  void anInvalidValueFailsStartup() {
    runner
        .withPropertyValues(
            "masternova.web-url=https://masternova.dev", "masternova.checkout.cart-max-items=0")
        .run(
            ctx -> {
              assertThat(ctx)
                  .hasFailed(); // ⭐ fail at boot — not on the first checkout in production
              assertThat(ctx.getStartupFailure())
                  .hasRootCauseInstanceOf(BindValidationException.class);
            });
  }

  @Test
  void aMissingRequiredValueFailsStartup() {
    runner.run(ctx -> assertThat(ctx).hasFailed()); // web-url is @NotNull
  }
}
