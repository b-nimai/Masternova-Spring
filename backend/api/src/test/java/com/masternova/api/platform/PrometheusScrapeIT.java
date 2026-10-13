package com.masternova.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * What Prometheus scrapes from the api (D3.1). ⭐ Boot turns metric export OFF in tests; {@code
 * AutoConfigureMetrics} turns the real Prometheus registry back on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class PrometheusScrapeIT {

  @Autowired MockMvcTester mvc;

  @Test
  void theScrapeIsPublicAndCarriesRedJvmAndPoolMetricsLabelledWithTheApp() throws Exception {
    assertThat(mvc.get().uri("/api/v1/meta/ping")).hasStatusOk(); // one request to measure

    var scrape = mvc.get().uri("/actuator/prometheus").exchange(); // no token: Prometheus has none

    assertThat(scrape).hasStatusOk();
    String body = scrape.getResponse().getContentAsString();
    assertThat(body)
        // RED: rate + errors (status) + duration, as a histogram with the SLO's exact 300 ms edge
        .contains("http_server_requests_seconds_bucket{application=\"api\"")
        .containsPattern(
            "http_server_requests_seconds_bucket\\{[^}]*uri=\"/api/v1/meta/ping\",le=\"0\\.3\"}")
        // USE: the JVM and the connection pool
        .contains("jvm_memory_used_bytes{application=\"api\"")
        .contains("hikaricp_connections_active{application=\"api\"")
        // the outbox debt is reported where events are WRITTEN too, not only where they're relayed
        .contains("outbox_overdue_seconds{application=\"api\"}");
  }
}
