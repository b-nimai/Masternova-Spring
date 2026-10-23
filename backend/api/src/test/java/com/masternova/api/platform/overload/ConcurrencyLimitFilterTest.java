package com.masternova.api.platform.overload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.platform.MasternovaProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** The bulkhead: a full pod answers 503 OVERLOADED at once, and every permit comes back. */
class ConcurrencyLimitFilterTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final ConcurrencyLimitFilter filter =
      new ConcurrencyLimitFilter(
          new MasternovaProperties(
              URI.create("http://localhost"),
              new MasternovaProperties.Checkout(20, Duration.ofSeconds(10)),
              new MasternovaProperties.Idempotency(Duration.ofHours(24), Duration.ofSeconds(30)),
              new MasternovaProperties.Overload(1, Duration.ofMillis(20))),
          meters,
          JsonMapper.builder().build());

  @Test
  void full_pod_answers_503_overloaded_with_retry_after() throws Exception {
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<MockHttpServletResponse> holder =
          pool.submit(
              () ->
                  run(
                      "/api/v1/courses",
                      (req, res) -> {
                        inside.countDown();
                        await(release);
                      }));
      assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

      MockHttpServletResponse rejected = run("/api/v1/courses", new MockFilterChain());

      assertThat(rejected.getStatus()).isEqualTo(503);
      assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
      assertThat(rejected.getContentType()).isEqualTo("application/problem+json");
      assertThat(rejected.getContentAsString()).contains("\"code\":\"OVERLOADED\"");
      assertThat(meters.counter("masternova.overload.rejected").count()).isEqualTo(1);
      assertThat(meters.get("masternova.overload.in.flight").gauge().value()).isEqualTo(1);

      release.countDown();
      assertThat(holder.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
    }
    // the permit came back: the next request runs
    assertThat(run("/api/v1/courses", new MockFilterChain()).getStatus()).isEqualTo(200);
  }

  @Test
  void a_throwing_handler_gives_its_permit_back() throws Exception {
    FilterChain failing =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };
    assertThatThrownBy(() -> run("/api/v1/courses", failing))
        .isInstanceOf(IllegalStateException.class);

    assertThat(run("/api/v1/courses", new MockFilterChain()).getStatus()).isEqualTo(200);
  }

  @Test
  void probes_and_non_api_paths_are_never_limited() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch inside = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      pool.submit(
          () ->
              run(
                  "/api/v1/courses",
                  (req, res) -> {
                    inside.countDown();
                    await(release);
                  }));
      assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(run("/actuator/health/liveness", new MockFilterChain()).getStatus())
          .isEqualTo(200);
      release.countDown();
    }
  }

  private MockHttpServletResponse run(String uri, FilterChain chain) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
