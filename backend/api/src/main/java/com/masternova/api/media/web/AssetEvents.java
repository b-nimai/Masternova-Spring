package com.masternova.api.media.web;

import com.masternova.api.media.application.AssetProgressService.Feed;
import com.masternova.api.media.application.AssetProgressView;
import com.masternova.api.media.web.dto.AssetProgressResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SSE side of the progress stream (API conventions §9): the worker publishes to Redis {@code
 * media:asset:{id}}; every api replica listens and pushes to ITS open streams for that asset. No
 * sticky sessions: whichever replica holds the browser's connection hears the message.
 *
 * <ul>
 *   <li>on open: register FIRST, then send the snapshot — a message arriving in between is a
 *       duplicate, never a gap;
 *   <li>{@code progress}: forwarded as is (lossy ticks);
 *   <li>{@code changed}: a fresh snapshot from the database (the truth), and the stream completes
 *       at READY or FAILED;
 *   <li>a comment every 20 s keeps proxies (nginx: 60 s) from closing an idle stream.
 * </ul>
 */
@Component
class AssetEvents implements MessageListener, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(AssetEvents.class);
  static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

  private record Subscriber(SseEmitter emitter, Feed feed) {}

  private final Map<UUID, Set<Subscriber>> byAsset = new ConcurrentHashMap<>();
  private final JsonMapper json;
  private final ScheduledExecutorService keepAlive =
      Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

  AssetEvents(JsonMapper json) {
    this.json = json;
    keepAlive.scheduleAtFixedRate(this::ping, 20, 20, TimeUnit.SECONDS);
  }

  SseEmitter open(UUID assetId, Feed feed) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
    Subscriber sub = new Subscriber(emitter, feed);
    Set<Subscriber> subs = byAsset.computeIfAbsent(assetId, id -> ConcurrentHashMap.newKeySet());
    subs.add(sub);
    Runnable remove = () -> unsubscribe(assetId, sub);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(e -> remove.run());
    send(assetId, sub, feed.initial());
    return emitter;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      JsonNode event = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
      UUID assetId = UUID.fromString(event.path("assetId").asString());
      Set<Subscriber> subs = byAsset.get(assetId);
      if (subs == null) {
        return; // nobody on this replica watches this asset
      }
      for (Subscriber sub : subs) {
        if (event.path("kind").asString().equals("progress")) {
          event(sub, "progress", event);
        } else {
          send(assetId, sub, sub.feed().refresh().get());
        }
      }
    } catch (RuntimeException e) {
      log.debug("ignoring a malformed progress message: {}", e.getMessage());
    }
  }

  /** A snapshot; at a terminal status, also the end of the stream. */
  private void send(UUID assetId, Subscriber sub, AssetProgressView view) {
    if (event(sub, "snapshot", AssetProgressResponse.from(view)) && view.terminal()) {
      sub.emitter().complete();
      unsubscribe(assetId, sub);
    }
  }

  private boolean event(Subscriber sub, String name, Object data) {
    try {
      sub.emitter().send(SseEmitter.event().name(name).data(data));
      return true;
    } catch (IOException | IllegalStateException e) {
      sub.emitter().completeWithError(e); // the browser went away
      return false;
    }
  }

  private void ping() {
    byAsset.values().stream()
        .flatMap(Set::stream)
        .forEach(
            sub -> {
              try {
                sub.emitter().send(SseEmitter.event().comment("keep-alive"));
              } catch (IOException | IllegalStateException e) {
                sub.emitter().completeWithError(e);
              }
            });
  }

  private void unsubscribe(UUID assetId, Subscriber sub) {
    byAsset.computeIfPresent(
        assetId, (id, subs) -> subs.remove(sub) && subs.isEmpty() ? null : subs);
  }

  /** Streams currently open on this replica (tests, and later a gauge in D3). */
  int openStreams() {
    return byAsset.values().stream().mapToInt(Set::size).sum();
  }

  @Override
  public void close() {
    keepAlive.shutdownNow();
  }
}
