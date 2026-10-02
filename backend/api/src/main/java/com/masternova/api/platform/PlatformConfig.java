package com.masternova.api.platform;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlatformConfig {

  /** Inject time instead of calling Instant.now() — tests can then pin it with a fixed Clock. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
