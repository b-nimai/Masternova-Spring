package com.masternova.api.identity.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.api.platform.ValidationException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin operations on users. ⭐ The role check is on the SERVICE, not the controller: whoever calls
 * changeRoles — a controller, a scheduled job, another module — gets the same rule. It's a proxy
 * feature (note 09): an internal this.changeRoles(...) call would bypass it.
 */
@Service
public class UserAdminService {

  private final UserRepository users;

  UserAdminService(UserRepository users) {
    this.users = users;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public User get(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public User changeRoles(CurrentUser actor, UUID userId, Set<Role> newRoles) {
    if (newRoles.isEmpty()) {
      throw ValidationException.of("roles", "NotEmpty", "A user needs at least one role.");
    }
    if (actor.id().equals(userId) && !newRoles.contains(Role.ADMIN)) {
      // ⭐ lockout protection: the last admin could otherwise remove the only way back in
      throw new RuleViolationException(
          "CANNOT_DEMOTE_SELF", "You cannot remove your own ADMIN role.");
    }
    User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    newRoles.forEach(user::grant); //                                  grant first …
    EnumSet.complementOf(EnumSet.copyOf(newRoles))
        .forEach(
            r -> {
              if (user.roles().contains(r)) {
                user.revoke(
                    r); //                                              … then revoke: never zero
                // roles
              }
            });
    return user; // managed entity: dirty checking writes it at commit
  }
}
