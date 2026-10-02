package com.masternova.api.identity.web;

import com.masternova.api.identity.domain.UserRepository;
import com.masternova.api.identity.web.dto.UserResponse;
import com.masternova.api.platform.NotFoundException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I?" — requires a valid access token (deny-by-default does that; no annotation needed).
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

  private final UserRepository users;

  MeController(UserRepository users) {
    this.users = users;
  }

  @GetMapping
  UserResponse me(@AuthenticationPrincipal Jwt token) {
    UUID userId = UUID.fromString(token.getSubject());
    return users
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(() -> new NotFoundException("User", userId));
  }
}
