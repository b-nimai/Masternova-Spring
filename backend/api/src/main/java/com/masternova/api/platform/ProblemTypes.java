package com.masternova.api.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds problems in the one Masternova shape, for code that runs OUTSIDE Spring MVC's exception
 * handling — servlet filters (idempotency) and the security chain (identity's 401/403). Part of the
 * platform module's PUBLIC API: ModularityTests rejected identity using it while it lived in the
 * internal platform.web package.
 */
public final class ProblemTypes {

  private static final String TYPE_BASE = "https://masternova.dev/problems/";

  private ProblemTypes() {}

  /** {@code VERSION_CONFLICT} → {@code https://masternova.dev/problems/version-conflict} */
  public static URI type(String code) {
    return URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-'));
  }

  public static ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type(code));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    return problem;
  }

  /** Writes a problem straight to a servlet response: for filters, which run outside Spring MVC. */
  public static void write(HttpServletResponse response, JsonMapper json, ProblemDetail problem)
      throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(json.writeValueAsBytes(problem));
  }
}
