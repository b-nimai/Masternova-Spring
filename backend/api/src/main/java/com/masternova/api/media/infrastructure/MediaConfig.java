package com.masternova.api.media.infrastructure;

import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MediaConfig {

  /** The playlists are fetched by the player itself: authorised by the ticket, not the bearer. */
  @Bean
  PublicEndpoints mediaPublicEndpoints() {
    return () -> List.of(Endpoint.get("/api/v1/media/assets/*/hls/**"));
  }
}
