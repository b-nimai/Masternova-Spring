package com.masternova.api.platform.overload;

import com.masternova.api.platform.MasternovaProperties;
import com.masternova.api.platform.ProblemTypes;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caps how many api requests one pod runs at once; past the cap, a fast 503 {@code OVERLOADED} with
 * {@code Retry-After} instead of a queue.
 *
 * <p>⭐ Why (D5.1, docs/hld/03-capacity.md): virtual threads mean Tomcat never says no. Above ~150
 * rps each pod held ~1,500 requests in memory behind 10 database connections, until the JVM stopped
 * answering its probes and restarted — and its traffic sank the next pod. A Semaphore Bulkhead
 * turns "everyone waits 60 s" into "a few get a 503 now, the rest stay fast".
 *
 * <p>Runs after the observation filter (a rejected request is still measured: the SLO counts it)
 * and before Spring Security (a rejected request costs no JWT check). {@code /actuator} is never
 * limited: a probe must not be shed by the overload it is there to report. An SSE stream holds its
 * permit only until its async start returns.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@DesignPattern(value = Pattern.BULKHEAD, role = "Bulkhead", note = "patterns/docs/18-bulkhead.md")
class ConcurrencyLimitFilter extends OncePerRequestFilter {

  private final Semaphore permits;
  private final long maxWaitNanos;
  private final Counter rejected;
  private final JsonMapper json;

  ConcurrencyLimitFilter(MasternovaProperties properties, MeterRegistry meters, JsonMapper json) {
    MasternovaProperties.Overload settings = properties.overload();
    this.permits = new Semaphore(settings.maxConcurrent());
    this.maxWaitNanos = settings.maxWait().toNanos();
    this.json = json;
    this.rejected =
        Counter.builder("masternova.overload.rejected")
            .description("Requests answered 503 OVERLOADED by the concurrency limit")
            .register(meters);
    Gauge.builder(
            "masternova.overload.in.flight",
            permits,
            p -> settings.maxConcurrent() - p.availablePermits())
        .description("Requests holding a concurrency-limit permit")
        .register(meters);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean acquired;
    try {
      acquired = permits.tryAcquire(maxWaitNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      acquired = false;
    }
    if (!acquired) {
      rejected.increment();
      reject(request, response);
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      permits.release(); // ⭐ in finally: a throwing handler must not leak a permit
    }
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.RETRY_AFTER, "1");
    ProblemTypes.write(
        response,
        json,
        ProblemTypes.problem(
            HttpStatus.SERVICE_UNAVAILABLE,
            "OVERLOADED",
            "The service is busy. Retry shortly.",
            request));
  }
}
