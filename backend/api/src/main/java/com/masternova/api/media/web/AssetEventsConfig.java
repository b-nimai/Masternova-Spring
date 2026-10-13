package com.masternova.api.media.web;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * One Redis subscription per api replica, for every asset: {@code media:asset:*}.
 *
 * <p>⭐ Started in the BACKGROUND, retrying, not as part of the context: Redis being down at boot
 * must not stop the api from starting — live progress is a convenience (the one-shot snapshot still
 * answers), logins and checkouts are not.
 */
@Configuration(proxyBeanMethods = false)
class AssetEventsConfig {

  private static final Logger log = LoggerFactory.getLogger(AssetEventsConfig.class);
  static final String CHANNELS = "media:asset:*";
  static final Duration RETRY = Duration.ofSeconds(5);

  @Bean
  RedisMessageListenerContainer assetEventsListener(
      RedisConnectionFactory redis, AssetEvents events) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redis);
    container.addMessageListener(events, new PatternTopic(CHANNELS));
    container.setAutoStartup(false);
    return container;
  }

  @EventListener(ApplicationReadyEvent.class)
  void subscribe(ApplicationReadyEvent ready) {
    RedisMessageListenerContainer container =
        ready
            .getApplicationContext()
            .getBean("assetEventsListener", RedisMessageListenerContainer.class);
    Thread.ofVirtual()
        .name("asset-events-subscriber")
        .start(
            () -> {
              while (!container.isRunning()) {
                try {
                  container.start();
                } catch (RuntimeException e) {
                  log.warn("live progress unavailable, retrying in {}: {}", RETRY, e.getMessage());
                  try {
                    Thread.sleep(RETRY);
                  } catch (InterruptedException stop) {
                    return;
                  }
                }
              }
            });
  }
}
