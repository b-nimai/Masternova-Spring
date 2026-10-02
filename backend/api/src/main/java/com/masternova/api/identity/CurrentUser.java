package com.masternova.api.identity;

import java.util.Set;
import java.util.UUID;

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
}
