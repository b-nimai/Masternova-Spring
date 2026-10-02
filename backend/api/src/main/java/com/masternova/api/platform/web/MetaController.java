package com.masternova.api.platform.web;

import com.masternova.api.platform.web.dto.PingResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness of the API from a client's point of view, plus which build is answering. */
@RestController
@RequestMapping("/api/v1/meta")
class MetaController {

  private final Clock clock;
  private final String version;

  MetaController(Clock clock, ObjectProvider<BuildProperties> buildProperties) {
    this.clock = clock;
    // build-info.properties exists only in Maven-built jars; fall back when run from an IDE.
    this.version =
        buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
  }

  @GetMapping("/ping")
  PingResponse ping() {
    return new PingResponse("UP", version, Instant.now(clock));
  }
}
