package com.masternova.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⭐ One id ties the signals together (D3.3): a log line written while a request runs carries the
 * request's trace id, and the outbox row it wrote carries the same id to the worker. Logs are the
 * ECS JSON the containers emit ({@code LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs} in the Dockerfile).
 * Tests don't EXPORT spans, but the tracer runs, so the ids are real.
 */
@SpringBootTest(
    properties = {
      "logging.structured.format.console=ecs",
      "masternova.identity.log-verification-links=true"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TraceCorrelationIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  final JsonMapper json = new JsonMapper();

  @Test
  void theRequestsLogLinesAndItsOutboxRowShareOneTraceId(CapturedOutput output) {
    String email = "trace-" + UUID.randomUUID() + "@example.com";

    assertThat(
            mvc.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","displayName":"Trace","password":"Sup3r-secret-pass"}
                    """
                        .formatted(email)))
        .hasStatus(201);

    JsonNode line =
        output
            .getOut()
            .lines()
            .filter(l -> l.startsWith("{") && l.contains(email))
            .map(json::readTree)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no JSON log line for " + email));
    // Boot's ECS writer adds the MDC as top-level traceId / spanId (not ECS's nested trace.id):
    // the field Loki extracts to link a log line to its trace (D3.4)
    String traceId = line.path("traceId").asString();
    assertThat(traceId).matches("\\p{XDigit}{32}");
    assertThat(line.path("spanId").asString()).matches("\\p{XDigit}{16}");

    String traceparent =
        jdbc.sql(
                """
                SELECT headers->>'traceparent' FROM outbox_message
                 WHERE event_type = 'identity.user-registered.v1' AND payload->>'email' = :email
                """)
            .param("email", email)
            .query(String.class)
            .single();
    assertThat(traceparent).startsWith("00-" + traceId + "-"); // ⭐ the worker continues THIS trace
  }
}
