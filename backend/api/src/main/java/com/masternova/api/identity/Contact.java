package com.masternova.api.identity;

import java.util.Objects;

/**
 * How to reach a user — part of identity's PUBLIC API. Added in Phase 9: a receipt is emailed from
 * the worker, so the purchase event must carry the address (the worker has no user table to ask).
 */
public record Contact(String email, String displayName) {
  public Contact {
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
  }
}
