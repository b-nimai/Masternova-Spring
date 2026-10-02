package com.masternova.api.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds problems in the one Masternova shape, for code that runs OUTSIDE Spring MVC's exception
 * handling (servlet filters, e.g. the idempotency filter).
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
}
