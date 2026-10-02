package com.masternova.api.catalog.web;

import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import java.util.Optional;

/** identity's {@link CurrentUser} (from the token) → catalog's own {@link Viewer}. */
final class Viewers {

  private Viewers() {}

  static Viewer of(Optional<CurrentUser> user) {
    return user.map(Viewers::of).orElseGet(Viewer::anonymous);
  }

  static Viewer of(CurrentUser user) {
    return user.has(Role.ADMIN) ? new Viewer.Admin(user.id()) : new Viewer.Member(user.id());
  }
}
