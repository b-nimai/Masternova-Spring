package com.masternova.api.identity.web;

import com.masternova.api.identity.application.EmailVerificationService;
import com.masternova.api.identity.application.SignupService;
import com.masternova.api.identity.application.SignupService.SignupCommand;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.web.dto.SignupRequest;
import com.masternova.api.identity.web.dto.UserResponse;
import com.masternova.api.identity.web.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller: validate, call ONE service, map to a DTO (CLAUDE.md §3). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

  private final SignupService signup;
  private final EmailVerificationService verification;

  AuthController(SignupService signup, EmailVerificationService verification) {
    this.signup = signup;
    this.verification = verification;
  }

  @PostMapping("/signup")
  ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    User user =
        signup.signup(
            new SignupCommand(request.email(), request.displayName(), request.password()));
    return ResponseEntity.created(URI.create("/api/v1/me")).body(UserResponse.from(user));
  }

  @PostMapping("/verify-email")
  ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    verification.verify(request.token());
    return ResponseEntity.noContent().build();
  }
}
