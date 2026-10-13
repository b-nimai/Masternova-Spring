package com.masternova.api.media.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.media.application.AssetProgressService;
import com.masternova.api.media.web.dto.AssetProgressResponse;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * An asset's processing progress, two ways (API conventions §9): a one-shot {@code GET} for clients
 * that poll, and {@code GET …/stream} ({@code text/event-stream}) that sends a snapshot, then live
 * progress, and closes at READY or FAILED.
 */
@RestController
@RequestMapping("/api/v1/media/assets/{id}")
class AssetController {

  private final AssetProgressService progress;
  private final AssetEvents events;

  AssetController(AssetProgressService progress, AssetEvents events) {
    this.progress = progress;
    this.events = events;
  }

  @GetMapping
  AssetProgressResponse get(@PathVariable UUID id, CurrentUser me) {
    return AssetProgressResponse.from(progress.snapshot(me, id));
  }

  /** ⭐ no-cache + X-Accel-Buffering: nginx and ALBs buffer responses unless told not to. */
  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<SseEmitter> stream(@PathVariable UUID id, CurrentUser me) {
    SseEmitter emitter = events.open(id, progress.open(me, id));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }
}
