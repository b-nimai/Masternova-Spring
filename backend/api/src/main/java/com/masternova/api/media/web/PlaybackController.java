package com.masternova.api.media.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.media.application.PlaybackService;
import com.masternova.api.media.web.dto.PlaybackResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playback (ADR-0013). {@code POST …/playback} is behind the bearer token and runs the access
 * check; the two playlist GETs are PUBLIC routes authorised by the ticket in {@code ?t=} (a player
 * fetches them itself, without our headers).
 */
@RestController
@RequestMapping("/api/v1/media/assets/{id}")
class PlaybackController {

  static final String HLS_VALUE = "application/vnd.apple.mpegurl";
  static final MediaType HLS = MediaType.parseMediaType(HLS_VALUE);

  private final PlaybackService playback;

  PlaybackController(PlaybackService playback) {
    this.playback = playback;
  }

  @PostMapping("/playback")
  PlaybackResponse ticket(@PathVariable UUID id, CurrentUser me) {
    return PlaybackResponse.from(playback.ticket(me, id));
  }

  @GetMapping(value = "/hls/master.m3u8", produces = HLS_VALUE) // never text/html
  ResponseEntity<String> master(@PathVariable UUID id, @RequestParam("t") String ticket) {
    return playlist(playback.master(id, ticket));
  }

  @GetMapping(value = "/hls/{rung:[0-9]{2,4}p}/index.m3u8", produces = HLS_VALUE)
  ResponseEntity<String> rung(
      @PathVariable UUID id, @PathVariable String rung, @RequestParam("t") String ticket) {
    return playlist(playback.rung(id, rung, ticket));
  }

  /** Personal (the urls inside are this viewer's): private, briefly cacheable. */
  private static ResponseEntity<String> playlist(String body) {
    return ResponseEntity.ok()
        .contentType(HLS)
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
        .body(body);
  }
}
