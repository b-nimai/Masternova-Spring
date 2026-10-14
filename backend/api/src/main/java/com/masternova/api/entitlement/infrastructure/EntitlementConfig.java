package com.masternova.api.entitlement.infrastructure;

import com.masternova.api.entitlement.application.EntitlementProperties;
import com.masternova.api.entitlement.domain.EntitlementRepository;
import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class EntitlementConfig {

  /** Anyone may ask what they may watch; a bearer token, when sent, makes it personal. */
  @Bean
  PublicEndpoints entitlementPublicEndpoints() {
    return () ->
        List.of(
            Endpoint.get("/api/v1/courses/*/access"), Endpoint.get("/api/v1/lectures/*/access"));
  }

  /**
   * ⭐ The decoration, visible in one place: the cache wraps the JDBC repository. Only the wrapper
   * is a bean, so nothing can inject the uncached one by accident.
   */
  @Bean
  EntitlementRepository entitlementRepository(
      JdbcClient jdbc,
      StringRedisTemplate redis,
      JsonMapper json,
      EntitlementProperties props,
      MeterRegistry meters) {
    return new CachedEntitlementRepository(
        new JdbcEntitlementRepository(jdbc), redis, json, props.cacheTtl(), meters);
  }
}
