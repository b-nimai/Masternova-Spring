package com.masternova.api.platform.idempotency;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the required-key interceptor and cleans up expired keys. */
@Configuration(proxyBeanMethods = false)
class IdempotencyConfig implements WebMvcConfigurer {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyConfig.class);

  private final IdempotencyStore store;
  private final Clock clock;

  IdempotencyConfig(IdempotencyStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new IdempotencyKeyRequiredInterceptor());
  }

  /** Expired keys (older than the retention) are deleted hourly. */
  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
  void deleteExpiredKeys() {
    int deleted = store.deleteExpired(clock.instant());
    if (deleted > 0) {
      log.info("Deleted {} expired idempotency keys", deleted);
    }
  }
}
