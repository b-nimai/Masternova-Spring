package com.masternova.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Boots the worker against real Postgres + Redis, with metrics export on (D3.1). */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class WorkerApplicationIT {

  @Autowired ApplicationContext context;
  @Autowired MockMvcTester mvc;

  @Test
  void contextLoads() {
    assertThat(context.getBean(WorkerApplication.class)).isNotNull();
  }

  @Test
  void prometheusSeesTheOutboxGaugesAndTheAppLabel() throws Exception {
    // (the pipeline's gauges come with its poller, which tests switch off: JobPollerTest)
    var scrape = mvc.get().uri("/actuator/prometheus").exchange();

    assertThat(scrape).hasStatusOk();
    assertThat(scrape.getResponse().getContentAsString())
        .contains("outbox_backlog{application=\"worker\"}")
        .contains("outbox_oldest_age_seconds{application=\"worker\"}");
  }
}
