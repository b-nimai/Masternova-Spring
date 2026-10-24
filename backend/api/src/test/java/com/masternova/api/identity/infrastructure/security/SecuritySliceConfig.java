package com.masternova.api.identity.infrastructure.security;

import com.masternova.api.identity.IdentityProperties;
import com.masternova.api.platform.MasternovaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The REAL security chain for {@code @WebMvcTest} slices. A slice only scans web types
 * (controllers, advice, filters, WebMvcConfigurers) — not {@code @Configuration} classes — so the
 * chain has to be imported. Public and in this package because the configs it imports are
 * package-private (module-internal). The slice also skips {@code @ConfigurationPropertiesScan}, so
 * the JWT secret's properties are enabled explicitly, and so are the platform's: the bulkhead
 * filter (D5.1) is part of the chain the slice runs, with its meters in a simple registry.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, ProblemSecurityHandlers.class, JwtConfig.class})
@EnableConfigurationProperties({IdentityProperties.class, MasternovaProperties.class})
public class SecuritySliceConfig {

  @Bean
  @ConditionalOnMissingBean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }
}
