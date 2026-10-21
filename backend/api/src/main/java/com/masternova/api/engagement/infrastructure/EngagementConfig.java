package com.masternova.api.engagement.infrastructure;

import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class EngagementConfig {

  /** A published course's reviews are part of its public page; writing one needs a learner. */
  @Bean
  PublicEndpoints engagementPublicEndpoints() {
    return () ->
        List.of(
            Endpoint.get("/api/v1/courses/*/reviews"),
            Endpoint.get("/api/v1/courses/*/reviews/summary"));
  }
}
