package com.masternova.api.identity.web.dto;

import com.masternova.api.identity.Role;
import com.masternova.api.identity.domain.User;
import java.util.List;

/** What the API says about a user. Never the entity itself (CLAUDE.md §3). */
public record UserResponse(
    String id, String email, String displayName, List<Role> roles, boolean emailVerified) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.id().toString(),
        user.email().value(),
        user.displayName(),
        user.roles().stream().sorted().toList(),
        user.isEmailVerified());
  }
}
