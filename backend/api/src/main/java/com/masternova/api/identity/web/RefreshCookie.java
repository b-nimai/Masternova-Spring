package com.masternova.api.identity.web;

import com.masternova.api.identity.IdentityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the refresh-token cookie with every protective flag in one place. */
@Component
class RefreshCookie {

  static final String NAME = "mn_refresh";
  private static final String PATH =
      "/api/v1/auth"; // ⭐ the browser sends it ONLY to auth endpoints

  private final IdentityProperties settings;

  RefreshCookie(IdentityProperties settings) {
    this.settings = settings;
  }

  ResponseCookie issue(String rawToken, Duration maxAge) {
    return base(rawToken).maxAge(maxAge).build();
  }

  /** Same name/path, empty value, Max-Age=0: the browser deletes it. */
  ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true) //                    ⭐ JavaScript can't read it — XSS can't steal it
        .secure(settings.secureCookies()) //  HTTPS only (off just for plain-HTTP local dev)
        .sameSite(
            "Strict") //                ⭐ never sent on cross-site requests — CSRF can't use it
        .path(PATH);
  }
}
