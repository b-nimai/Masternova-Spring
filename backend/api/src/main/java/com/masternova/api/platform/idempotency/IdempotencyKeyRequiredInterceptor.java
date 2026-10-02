package com.masternova.api.platform.idempotency;

import com.masternova.api.platform.IdempotencyKeyRequired;
import com.masternova.api.platform.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects calls to {@link IdempotencyKeyRequired} endpoints that forgot the header. An interceptor
 * (not the filter) because only Spring MVC knows which handler method a request maps to (note 10
 * §2). The thrown ValidationException becomes a 400 through GlobalExceptionHandler.
 */
class IdempotencyKeyRequiredInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (handler instanceof HandlerMethod method
        && method.hasMethodAnnotation(IdempotencyKeyRequired.class)
        && request.getHeader(IdempotencyFilter.HEADER) == null) {
      throw ValidationException.of(
          IdempotencyFilter.HEADER,
          "Required",
          "This endpoint requires an Idempotency-Key header.");
    }
    return true;
  }
}
