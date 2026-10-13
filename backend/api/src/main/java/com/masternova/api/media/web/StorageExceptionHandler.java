package com.masternova.api.media.web;

import com.masternova.api.platform.ProblemTypes;
import com.masternova.storage.StorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Storage being down is temporary: 503 {@code STORAGE_UNAVAILABLE} with {@code Retry-After}, not a
 * 500. ⭐ Ordered first: Spring picks the first ADVICE with a matching handler, and the platform's
 * catch-all {@code Exception} handler would otherwise win.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class StorageExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(StorageExceptionHandler.class);

  @ExceptionHandler(StorageUnavailableException.class)
  ResponseEntity<ProblemDetail> unavailable(
      StorageUnavailableException e, HttpServletRequest request) {
    log.warn("storage unavailable: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "5")
        .body(
            ProblemTypes.problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE",
                "Media storage is temporarily unavailable. Try again shortly.",
                request));
  }
}
