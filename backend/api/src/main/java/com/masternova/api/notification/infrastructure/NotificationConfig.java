package com.masternova.api.notification.infrastructure;

import com.masternova.api.notification.NotificationProperties;
import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import com.masternova.kernel.notification.UnsubscribeTokens;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class NotificationConfig {

  /** The kernel codec, keyed with the secret the worker signs with. */
  @Bean
  UnsubscribeTokens unsubscribeTokens(NotificationProperties properties) {
    return new UnsubscribeTokens(properties.tokenSecret());
  }

  /**
   * The unsubscribe endpoints are public: the signed token IS the credential (someone reading their
   * email in another browser isn't logged in). POST only — never a GET that changes state.
   */
  @Bean
  PublicEndpoints notificationPublicEndpoints() {
    return () -> List.of(new Endpoint(HttpMethod.POST, "/api/v1/notifications/unsubscribe/**"));
  }
}
