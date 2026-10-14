package com.masternova.api.entitlement.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is asking: a signed-in user (maybe an admin) or nobody. Anonymous viewers get decisions too —
 * the course page shows which lectures are open to everyone.
 */
public record Viewer(Optional<UUID> userId, boolean admin) {

  public Viewer {
    Objects.requireNonNull(userId, "userId");
    if (admin && userId.isEmpty()) {
      throw new IllegalArgumentException("an admin is always signed in");
    }
  }

  public static Viewer anonymous() {
    return new Viewer(Optional.empty(), false);
  }

  public static Viewer user(UUID id, boolean admin) {
    return new Viewer(Optional.of(id), admin);
  }
}
