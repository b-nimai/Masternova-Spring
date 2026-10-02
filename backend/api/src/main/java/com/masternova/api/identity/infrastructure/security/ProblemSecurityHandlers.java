package com.masternova.api.identity.infrastructure.security;

import com.masternova.api.platform.ProblemTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 and 403 raised INSIDE the security filter chain never reach @RestControllerAdvice (they
 * happen before any controller — note 10 §1). These handlers write the same Problem Details shape
 * so clients see ONE error format everywhere.
 */
@Component
class ProblemSecurityHandlers {

  private final JsonMapper json;
  private final BearerTokenAuthenticationEntryPoint bearer =
      new BearerTokenAuthenticationEntryPoint();

  ProblemSecurityHandlers(JsonMapper json) {
    this.json = json;
  }

  /** No/invalid/expired token → 401, keeping the standard WWW-Authenticate: Bearer header. */
  AuthenticationEntryPoint entryPoint() {
    return (request, response, exception) -> {
      bearer.commence(request, response, exception); // sets status + WWW-Authenticate (RFC 6750)
      write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", describe(exception));
    };
  }

  /** Valid token, missing role → 403. */
  AccessDeniedHandler accessDenied() {
    return (request, response, exception) -> {
      ProblemDetail problem =
          ProblemTypes.problem(
              HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this.", request);
      problem.setProperty("reason", "INSUFFICIENT_ROLE");
      send(response, HttpStatus.FORBIDDEN, problem);
    };
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    send(response, status, ProblemTypes.problem(status, code, detail, request));
  }

  private void send(HttpServletResponse response, HttpStatus status, ProblemDetail problem)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(json.writeValueAsBytes(problem));
  }

  private static String describe(AuthenticationException exception) {
    // never echo token-parsing details back to the caller
    return exception.getMessage() != null && exception.getMessage().contains("expired")
        ? "Your session token has expired."
        : "Authentication is required.";
  }
}
