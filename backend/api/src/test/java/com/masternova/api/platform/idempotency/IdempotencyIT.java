package com.masternova.api.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.platform.IdempotencyKeyRequired;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Idempotency-Key behaviour over REAL HTTP against a running server and Postgres (API conventions
 * §4). The concurrency proof (50 identical requests at once) lives in IdempotencyConcurrencyIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, IdempotencyIT.Endpoints.class})
class IdempotencyIT {

  /** A test-only endpoint: counts how often its body REALLY runs. */
  @RestController
  static class EnrollmentController {
    final AtomicInteger executions = new AtomicInteger();
    final AtomicInteger failuresLeft = new AtomicInteger();
    final AtomicInteger delayMillis = new AtomicInteger(); // makes concurrent requests overlap

    @PostMapping("/api/v1/test/enrollments")
    @IdempotencyKeyRequired
    ResponseEntity<Map<String, Object>> enroll(@RequestBody Map<String, String> body)
        throws InterruptedException {
      int run = executions.incrementAndGet();
      Thread.sleep(delayMillis.get());
      if (failuresLeft.getAndDecrement() > 0) {
        throw new IllegalStateException(
            "payment provider down"); // → 500 via GlobalExceptionHandler
      }
      return ResponseEntity.status(201)
          .body(Map.of("enrollmentNo", run, "courseId", body.get("courseId")));
    }

    @PostMapping("/api/v1/test/notes") // NOT annotated: the header is optional here
    ResponseEntity<Map<String, Object>> note(@RequestBody Map<String, String> body) {
      return ResponseEntity.ok(Map.of("noteNo", executions.incrementAndGet()));
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Endpoints {
    @Bean
    EnrollmentController enrollmentController() {
      return new EnrollmentController();
    }
  }

  @LocalServerPort int port;
  @Autowired EnrollmentController controller;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void reset() {
    controller.executions.set(0);
    controller.failuresLeft.set(0);
    controller.delayMillis.set(0);
  }

  private HttpResponse<String> post(String path, String key, String json) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String newKey() {
    return UUID.randomUUID().toString();
  }

  @Test
  void aRetryReplaysTheStoredResponseWithoutRunningTheHandlerAgain() throws Exception {
    String key = newKey();

    HttpResponse<String> first = post("/api/v1/test/enrollments", key, "{\"courseId\":\"c1\"}");
    HttpResponse<String> retry = post("/api/v1/test/enrollments", key, "{\"courseId\":\"c1\"}");

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(retry.statusCode()).isEqualTo(201);
    assertThat(retry.body()).isEqualTo(first.body()); // byte-for-byte the same response
    assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
    assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
    assertThat(controller.executions).hasValue(1); // ⭐ the effect happened ONCE
  }

  @Test
  void theSameKeyWithADifferentBodyIsRejected() throws Exception {
    String key = newKey();
    post("/api/v1/test/enrollments", key, "{\"courseId\":\"c1\"}");

    HttpResponse<String> reused = post("/api/v1/test/enrollments", key, "{\"courseId\":\"c2\"}");

    assertThat(reused.statusCode()).isEqualTo(422);
    assertThat(reused.body()).contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\"");
    assertThat(controller.executions).hasValue(1);
  }

  @Test
  void aRequiredKeyThatIsMissingIsA400() throws Exception {
    HttpResponse<String> response = post("/api/v1/test/enrollments", null, "{\"courseId\":\"c1\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"code\":\"VALIDATION_FAILED\"")
        .contains("Idempotency-Key");
    assertThat(controller.executions).hasValue(0);
  }

  @Test
  void aServerErrorReleasesTheKeySoTheRetryReallyRuns() throws Exception {
    String key = newKey();
    controller.failuresLeft.set(1);

    HttpResponse<String> failed = post("/api/v1/test/enrollments", key, "{\"courseId\":\"c1\"}");
    HttpResponse<String> retried = post("/api/v1/test/enrollments", key, "{\"courseId\":\"c1\"}");

    assertThat(failed.statusCode()).isEqualTo(500);
    assertThat(retried.statusCode()).isEqualTo(201); // NOT a replay of the 500
    assertThat(controller.executions).hasValue(2);
  }

  @Test
  void anyUnsafeRequestWithTheHeaderIsIdempotentEvenWithoutTheAnnotation() throws Exception {
    String key = newKey();

    post("/api/v1/test/notes", key, "{}");
    HttpResponse<String> retry = post("/api/v1/test/notes", key, "{}");

    assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
    assertThat(controller.executions).hasValue(1);
  }

  @Test
  void aMalformedKeyIsRejected() throws Exception {
    HttpResponse<String> response = post("/api/v1/test/notes", "x".repeat(201), "{}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("IDEMPOTENCY_KEY_INVALID");
  }

  /**
   * ⭐ THE PROOF (task 2.8): 50 identical requests — same key, same body — released at the same
   * instant against a real server and database. The handler is slow, so they genuinely overlap.
   */
  @Test
  void fiftyConcurrentIdenticalRequestsRunTheHandlerExactlyOnce() throws Exception {
    controller.delayMillis.set(300);
    String key = newKey();
    String body = "{\"courseId\":\"c-concurrent\"}";
    java.util.List<HttpResponse<String>> responses =
        java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);

    try (var clients = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < 50; i++) {
        clients.submit(
            () -> {
              start.await();
              responses.add(post("/api/v1/test/enrollments", key, body));
              return null;
            });
      }
      start.countDown();
    }

    assertThat(controller.executions).hasValue(1); // ⭐ 50 requests, ONE effect
    assertThat(responses).hasSize(50);
    java.util.Map<Integer, Long> byStatus =
        responses.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    HttpResponse::statusCode, java.util.stream.Collectors.counting()));
    assertThat(byStatus.keySet()).isSubsetOf(201, 409); // nothing else — no 500s, no duplicates
    assertThat(byStatus.get(201)).isGreaterThanOrEqualTo(1);
    // every success carries the ONE stored response
    assertThat(
            responses.stream()
                .filter(r -> r.statusCode() == 201)
                .map(HttpResponse::body)
                .distinct())
        .hasSize(1);
    // every 409 is the documented in-progress problem
    assertThat(responses.stream().filter(r -> r.statusCode() == 409))
        .allSatisfy(r -> assertThat(r.body()).contains("IDEMPOTENCY_IN_PROGRESS"));

    // and once it has finished, a late retry is a replay, still without a second execution
    HttpResponse<String> late = post("/api/v1/test/enrollments", key, body);
    assertThat(late.statusCode()).isEqualTo(201);
    assertThat(late.headers().firstValue("Idempotent-Replayed")).hasValue("true");
    assertThat(controller.executions).hasValue(1);
  }
}
