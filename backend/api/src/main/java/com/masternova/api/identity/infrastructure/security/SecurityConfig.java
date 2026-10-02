package com.masternova.api.identity.infrastructure.security;

import com.masternova.api.identity.Role;
import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API's security filter chain — Spring's CHAIN OF RESPONSIBILITY (bearer-token filter →
 * authorization → …). Owned by identity because identity owns authentication.
 *
 * <ul>
 *   <li>⭐ DENY BY DEFAULT: everything needs a valid access token except the routes modules declare
 *       through {@link PublicEndpoints} beans.
 *   <li>STATELESS: no HTTP session; every request carries its JWT.
 *   <li>Method security on: {@code @PreAuthorize("hasRole('ADMIN')")} on any bean method.
 *   <li>CSRF off: the only cookie (the refresh token) is SameSite=Strict and scoped to
 *       /api/v1/auth; every other call authenticates with a header a forged cross-site request
 *       can't attach.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
class SecurityConfig {

  /** Identity's own public routes: signup, login, refresh, logout, verify. */
  @Bean
  PublicEndpoints identityPublicEndpoints() {
    return () -> List.of(Endpoint.any("/api/v1/auth/**"));
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, List<PublicEndpoints> publicEndpoints, ProblemSecurityHandlers problems)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              // ⭐ every module's declared public routes …
              publicEndpoints.stream()
                  .flatMap(p -> p.endpoints().stream())
                  .forEach(
                      e -> {
                        if (e.method() == null) {
                          auth.requestMatchers(e.pattern()).permitAll();
                        } else {
                          auth.requestMatchers(e.method(), e.pattern()).permitAll();
                        }
                      });
              // … and EVERYTHING else requires authentication
              auth.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            o ->
                o.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken()))
                    .authenticationEntryPoint(problems.entryPoint()))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(problems.entryPoint())
                    .accessDeniedHandler(problems.accessDenied()))
        .build();
  }

  /** JWT "roles": ["ADMIN"] → authorities ROLE_ADMIN; the principal name is the user id (sub). */
  private static Converter<Jwt, AbstractAuthenticationToken> rolesFromToken() {
    return jwt -> {
      Collection<GrantedAuthority> authorities =
          jwt.getClaimAsStringList("roles") == null
              ? List.of()
              : jwt.getClaimAsStringList("roles").stream()
                  .map(
                      r ->
                          (GrantedAuthority)
                              new SimpleGrantedAuthority(Role.valueOf(r).authority()))
                  .toList();
      return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    };
  }
}
