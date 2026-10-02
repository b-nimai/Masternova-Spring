package com.masternova.api.catalog.infrastructure;

import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CatalogConfig {

  /**
   * The storefront is public: anyone may browse and open a course page (GET only). A bearer token,
   * when sent, is still verified — that's how an owner sees their own draft.
   */
  @Bean
  PublicEndpoints catalogPublicEndpoints() {
    return () ->
        List.of(
            Endpoint.get("/api/v1/courses"),
            Endpoint.get("/api/v1/courses/*"),
            Endpoint.get("/api/v1/categories"));
  }
}
