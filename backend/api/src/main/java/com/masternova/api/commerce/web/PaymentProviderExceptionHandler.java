package com.masternova.api.commerce.web;

import com.masternova.api.commerce.domain.PaymentProviderException;
import com.masternova.api.platform.ProblemTypes;
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
 * The provider failing is not our 500 (LLD §8): temporary → 503 {@code
 * PAYMENT_PROVIDER_UNAVAILABLE} with Retry-After (and the Idempotency-Key is released, so a retry
 * really retries); permanent → 502 {@code PAYMENT_PROVIDER_ERROR}. Ordered before the platform's
 * catch-all, like media's storage handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaymentProviderExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(PaymentProviderExceptionHandler.class);

  @ExceptionHandler(PaymentProviderException.class)
  ResponseEntity<ProblemDetail> providerFailed(
      PaymentProviderException e, HttpServletRequest request) {
    if (e.isTemporary()) {
      log.warn("payment provider unavailable: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .header(HttpHeaders.RETRY_AFTER, "5")
          .body(
              ProblemTypes.problem(
                  HttpStatus.SERVICE_UNAVAILABLE,
                  "PAYMENT_PROVIDER_UNAVAILABLE",
                  "Payments are temporarily unavailable. Try again shortly.",
                  request));
    }
    log.error("payment provider rejected our request: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(
            ProblemTypes.problem(
                HttpStatus.BAD_GATEWAY,
                "PAYMENT_PROVIDER_ERROR",
                "The payment provider refused this request. Nothing was charged.",
                request));
  }
}
