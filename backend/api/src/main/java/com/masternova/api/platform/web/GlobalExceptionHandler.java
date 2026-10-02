package com.masternova.api.platform.web;

import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.DomainException;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.ProblemTypes;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.api.platform.UnauthenticatedException;
import com.masternova.api.platform.ValidationException;
import com.masternova.api.platform.ValidationException.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The ONE place errors become HTTP responses: RFC 9457 {@code application/problem+json}, always
 * with a stable {@code code} (API conventions §1).
 *
 * <ul>
 *   <li>Domain errors → an exhaustive switch over the sealed {@link DomainException} hierarchy.
 *   <li>Bean Validation failures → 400 {@code VALIDATION_FAILED} with {@code errors[]}, the same
 *       shape as {@link ValidationException}.
 *   <li>Spring MVC's own errors (404, 405, 415, malformed JSON …) → the base class builds the
 *       problem; we add a {@code code}.
 *   <li>Anything else is a bug → 500 {@code INTERNAL}, logged with its stack trace, message never
 *       sent to the client.
 * </ul>
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
    // ⭐ Exhaustive over the sealed hierarchy: add a kind and this stops compiling (note 02 §5).
    HttpStatus status =
        switch (ex) {
          case NotFoundException e -> HttpStatus.NOT_FOUND;
          case ValidationException e -> HttpStatus.BAD_REQUEST;
          case ForbiddenException e -> HttpStatus.FORBIDDEN;
          case ConflictException e -> HttpStatus.CONFLICT;
          case RuleViolationException e -> HttpStatus.UNPROCESSABLE_CONTENT;
          case UnauthenticatedException e -> HttpStatus.UNAUTHORIZED;
        };
    ProblemDetail problem = problem(status, ex.code(), ex.getMessage(), request);
    ex.details().forEach(problem::setProperty);
    return ResponseEntity.status(status).body(problem);
  }

  /** JPA's @Version clash (Phase 6) — without the versions, which only the service knows. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            problem(
                HttpStatus.CONFLICT,
                "VERSION_CONFLICT",
                "This was changed elsewhere. Reload and try again.",
                request));
  }

  /**
   * @PreAuthorize on a service method throws INSIDE Spring MVC — without this handler the catch-all
   * below would turn a missing role into a 500. Same shape as the security chain's 403.
   */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    ProblemDetail problem =
        problem(
            HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this.", request);
    problem.setProperty("reason", "INSUFFICIENT_ROLE");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Something went wrong.", request));
  }

  /**
   * @Valid on a @RequestBody failed — report every field, sorted for a stable response.
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors =
        ex.getFieldErrors().stream()
            .map(
                f ->
                    new FieldError(
                        f.getField(),
                        String.valueOf(f.getCode()),
                        String.valueOf(f.getDefaultMessage())))
            .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::code))
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request has invalid fields.");
    problem.setType(type("VALIDATION_FAILED"));
    problem.setProperty("code", "VALIDATION_FAILED");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  /**
   * Spring MVC's own exceptions: let the base class build its problem, then add a stable code. (The
   * body is usually still null when this is called — the base class creates it inside super — so we
   * decorate the RESULT, not the argument.)
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
      String code = codeFor(response.getStatusCode());
      problem.setType(type(code));
      problem.setProperty("code", code);
    }
    return response;
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    return ProblemTypes.problem(status, code, detail, request);
  }

  private static URI type(String code) {
    return ProblemTypes.type(code);
  }

  private static String codeFor(HttpStatusCode status) {
    HttpStatus known = HttpStatus.resolve(status.value());
    return known == null ? "HTTP_" + status.value() : known.name();
  }
}
