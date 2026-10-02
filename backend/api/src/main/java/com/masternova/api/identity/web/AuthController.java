package com.masternova.api.identity.web;

import com.masternova.api.identity.application.AuthService;
import com.masternova.api.identity.application.AuthService.Tokens;
import com.masternova.api.identity.application.EmailVerificationService;
import com.masternova.api.identity.application.SignupService;
import com.masternova.api.identity.application.SignupService.SignupCommand;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.web.dto.LoginRequest;
import com.masternova.api.identity.web.dto.SignupRequest;
import com.masternova.api.identity.web.dto.TokenResponse;
import com.masternova.api.identity.web.dto.UserResponse;
import com.masternova.api.identity.web.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public auth endpoints (declared public by identity's PublicEndpoints bean). Thin by design. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

  private final SignupService signup;
  private final EmailVerificationService verification;
  private final AuthService auth;
  private final RefreshCookie refreshCookie;
  private final Clock clock;

  AuthController(
      SignupService signup,
      EmailVerificationService verification,
      AuthService auth,
      RefreshCookie refreshCookie,
      Clock clock) {
    this.signup = signup;
    this.verification = verification;
    this.auth = auth;
    this.refreshCookie = refreshCookie;
    this.clock = clock;
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

  @PostMapping("/login")
  ResponseEntity<TokenResponse> login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return respond(auth.login(request.email(), request.password(), userAgent));
  }

  /** No body: the refresh token arrives in its httpOnly cookie. */
  @PostMapping("/refresh")
  ResponseEntity<TokenResponse> refresh(
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    return respond(auth.refresh(refreshToken));
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    auth.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
        .build();
  }

  private ResponseEntity<TokenResponse> respond(Tokens tokens) {
    long expiresIn =
        Duration.between(clock.instant(), tokens.accessToken().expiresAt()).toSeconds();
    Duration refreshMaxAge = Duration.between(clock.instant(), tokens.refreshExpiresAt());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie.issue(tokens.refreshToken(), refreshMaxAge).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store") // tokens must never be cached
        .body(
            new TokenResponse(
                tokens.accessToken().value(),
                "Bearer",
                expiresIn,
                UserResponse.from(tokens.user())));
  }
}
