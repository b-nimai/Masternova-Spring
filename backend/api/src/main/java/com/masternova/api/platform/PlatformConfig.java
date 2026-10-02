package com.masternova.api.platform;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling // the outbox relay (and later cleanup jobs) run on @Scheduled
class PlatformConfig {

  /** Inject time instead of calling Instant.now() — tests can then pin it with a fixed Clock. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** The platform's own public routes: service metadata, health probes, the error page. */
  @Bean
  PublicEndpoints platformPublicEndpoints() {
    return () ->
        List.of(
            PublicEndpoints.Endpoint.get("/api/v1/meta/**"),
            PublicEndpoints.Endpoint.get("/actuator/health/**"),
            PublicEndpoints.Endpoint.get("/actuator/info"),
            PublicEndpoints.Endpoint.any("/error"));
  }
}
