package com.masternova.api.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder security: stateless and open. Phase 3 (identity) replaces {@code permitAll} with JWT
 * authentication and role rules.
 *
 * <p>Worth noticing already: {@link SecurityFilterChain} is Spring's own Chain of Responsibility —
 * each filter either handles the request or passes it on.
 */
// Only for a servlet web app: HttpSecurity doesn't exist in a non-web context (e.g. a
// @SpringBootTest with webEnvironment = NONE, or a future batch-only profile).
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }
}
