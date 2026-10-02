package com.masternova.api.platform.idempotency;

import com.masternova.api.platform.MasternovaProperties;
import com.masternova.api.platform.idempotency.IdempotencyStore.Claim;
import com.masternova.api.platform.idempotency.IdempotencyStore.StoredResponse;
import com.masternova.api.platform.web.ProblemTypes;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes unsafe requests carrying an {@code Idempotency-Key} header safe to retry (API conventions
 * §4): the first request runs and its response is stored; a retry gets the SAME response without
 * running the handler again.
 *
 * <p>A servlet filter (Chain of Responsibility) — it runs after Spring Security (so the caller is
 * known) and before any controller. Design: docs/lld/platform-kernel.md §5.
 */
@Component
@DesignPattern(
    value = Pattern.CHAIN_OF_RESPONSIBILITY,
    role = "ConcreteHandler",
    note = "patterns/README.md")
class IdempotencyFilter extends OncePerRequestFilter {

  static final String HEADER = "Idempotency-Key";
  static final String REPLAYED_HEADER = "Idempotent-Replayed";
  private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final int MAX_KEY_LENGTH = 200;

  private final IdempotencyStore store;
  private final MasternovaProperties.Idempotency settings;
  private final Clock clock;
  private final JsonMapper json;

  IdempotencyFilter(
      IdempotencyStore store, MasternovaProperties properties, Clock clock, JsonMapper json) {
    this.store = store;
    this.settings = properties.idempotency();
    this.clock = clock;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // only unsafe requests that opted in with the header (enforcement of "required" is done by
    // IdempotencyKeyRequiredInterceptor, which knows the target handler)
    return !UNSAFE_METHODS.contains(request.getMethod()) || request.getHeader(HEADER) == null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getHeader(HEADER).strip();
    if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
      writeProblem(
          request,
          response,
          HttpStatus.BAD_REQUEST,
          "IDEMPOTENCY_KEY_INVALID",
          "The Idempotency-Key header must be 1–" + MAX_KEY_LENGTH + " characters.");
      return;
    }

    byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
    String caller = caller();
    Instant now = clock.instant();
    Claim claim =
        store.claim(
            caller,
            key,
            hash(request, body),
            now,
            now.plus(settings.inProgressTimeout()),
            now.plus(settings.retention()));

    // ⭐ exhaustive over the sealed Claim type (note 02)
    switch (claim) {
      case Claim.Acquired a ->
          runAndRemember(new CachedBodyRequest(request, body), response, chain, caller, key);
      case Claim.Replay(StoredResponse stored) -> replay(response, stored);
      case Claim.InProgress p ->
          writeProblem(
              request,
              response,
              HttpStatus.CONFLICT,
              "IDEMPOTENCY_IN_PROGRESS",
              "A request with this Idempotency-Key is still being processed. Retry shortly.");
      case Claim.Mismatch m ->
          writeProblem(
              request,
              response,
              HttpStatus.UNPROCESSABLE_CONTENT,
              "IDEMPOTENCY_KEY_REUSED",
              "This Idempotency-Key was already used with a different request.");
    }
  }

  private void runAndRemember(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain,
      String caller,
      String key)
      throws ServletException, IOException {
    ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
    boolean completed = false;
    try {
      chain.doFilter(request, captured);
      if (captured.getStatus() < 500) {
        store.complete(
            caller,
            key,
            new StoredResponse(
                captured.getStatus(), captured.getContentType(), captured.getContentAsByteArray()));
        completed = true;
      }
    } finally {
      if (!completed) {
        store.release(
            caller, key); // ⭐ 5xx or an exception: forget the key so a retry really retries
      }
      captured.copyBodyToResponse(); // the body was buffered — send it to the client now
    }
  }

  private static void replay(HttpServletResponse response, StoredResponse stored)
      throws IOException {
    response.setStatus(stored.status());
    if (stored.contentType() != null) {
      response.setContentType(stored.contentType());
    }
    response.setHeader(REPLAYED_HEADER, "true");
    response.getOutputStream().write(stored.body() == null ? new byte[0] : stored.body());
  }

  private void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    ProblemDetail problem = ProblemTypes.problem(status, code, detail, request);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(json.writeValueAsBytes(problem));
  }

  /** Keys are scoped PER CALLER: one user can never see another user's stored response. */
  private static String caller() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()
        ? "anonymous"
        : "user:" + auth.getName();
  }

  /** SHA-256 of method + path + body: "the same request" means all three match. */
  private static String hash(HttpServletRequest request, byte[] body) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      sha.update(
          (request.getMethod() + " " + request.getRequestURI() + "\n")
              .getBytes(StandardCharsets.UTF_8));
      sha.update(body);
      return HexFormat.of().formatHex(sha.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }
}
