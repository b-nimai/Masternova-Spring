package com.masternova.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Who is calling — part of identity's PUBLIC API. Any module's controller can declare a {@code
 * CurrentUser me} parameter and gets it resolved from the access token (no database lookup).
 *
 * <p>Roles come from the token, so a role change takes effect at the next refresh (≤ 15 minutes).
 */
public record CurrentUser(UUID id, Set<Role> roles, boolean emailVerified) {

  public CurrentUser {
    roles = Set.copyOf(roles);
  }

  public boolean has(Role role) {
    return roles.contains(role);
  }

  /**
   * The caller behind a verified access token, or empty for anyone else (no token on a public
   * route: Spring's anonymous authentication). The ONE place a JWT becomes a {@code CurrentUser} —
   * the argument resolver and {@code @PreAuthorize} beans (entitlement's {@code @entitlements})
   * both use it.
   */
  public static Optional<CurrentUser> from(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwt)) {
      return Optional.empty();
    }
    List<String> roles = jwt.getToken().getClaimAsStringList("roles");
    return Optional.of(
        new CurrentUser(
            UUID.fromString(jwt.getToken().getSubject()),
            roles == null
                ? Set.of()
                : roles.stream().map(Role::valueOf).collect(Collectors.toSet()),
            Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("email_verified"))));
  }
}
