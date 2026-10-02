package com.masternova.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Boots the whole api against real Postgres + Redis and proves the skeleton is wired. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiApplicationIT {

  @Autowired MockMvcTester mvc;

  @Test
  void pingAnswers() {
    assertThat(mvc.get().uri("/api/v1/meta/ping"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void healthIsUpWithDatabaseAndRedis() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void theApiIsDenyByDefault() {
    // ⭐ An unknown route without a token is 401, not 404: we don't even reveal what exists.
    var result = mvc.get().uri("/api/v1/nope").exchange();

    assertThat(result)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    // RFC 6750 scheme; Spring Security 7 also appends RFC 9728 resource_metadata
    assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
  }
}
