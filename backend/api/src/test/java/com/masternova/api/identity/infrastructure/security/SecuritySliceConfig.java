package com.masternova.api.identity.infrastructure.security;

import com.masternova.api.identity.IdentityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The REAL security chain for {@code @WebMvcTest} slices. A slice only scans web types
 * (controllers, advice, filters, WebMvcConfigurers) — not {@code @Configuration} classes — so the
 * chain has to be imported. Public and in this package because the configs it imports are
 * package-private (module-internal). The slice also skips {@code @ConfigurationPropertiesScan}, so
 * the JWT secret's properties are enabled explicitly.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, ProblemSecurityHandlers.class, JwtConfig.class})
@EnableConfigurationProperties(IdentityProperties.class)
public class SecuritySliceConfig {}
