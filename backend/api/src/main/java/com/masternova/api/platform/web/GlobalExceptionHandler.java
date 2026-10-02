package com.masternova.api.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One error envelope for the whole API: RFC 9457 {@code application/problem+json}.
 *
 * <p>The base class already maps Spring MVC's own exceptions (400 validation, 404, 405, 415 …) to a
 * {@link ProblemDetail}. Domain exceptions get their handlers here as modules land; anything
 * unexpected becomes a 500 that never leaks the exception message.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
    problem.setTitle("Internal Server Error");
    return problem;
  }
}
