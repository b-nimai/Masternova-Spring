package com.masternova.api.entitlement.web;

import com.masternova.api.entitlement.application.LecturePlaybackService;
import com.masternova.api.entitlement.web.dto.LecturePlaybackResponse;
import com.masternova.api.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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
 * Learner playback (ADR-0017). {@code POST …/playback} is behind the bearer token and the
 * entitlement decision; the two playlist GETs are PUBLIC routes authorised by the token in {@code
 * ?token=} (the player fetches them itself, without our headers).
 *
 * <p>⭐ The client IP is {@code getRemoteAddr()}: with {@code server.forward-headers-strategy:
 * native}, Tomcat has already replaced nginx's address with the client's — taken from {@code
 * X-Forwarded-For} only when an internal proxy sent it.
 */
@RestController
@RequestMapping("/api/v1/lectures/{id}")
class LecturePlaybackController {

  private static final String HLS = "application/vnd.apple.mpegurl";
  private static final MediaType HLS_TYPE = MediaType.parseMediaType(HLS);

  private final LecturePlaybackService playback;

  LecturePlaybackController(LecturePlaybackService playback) {
    this.playback = playback;
  }

  @PostMapping("/playback")
  LecturePlaybackResponse issue(@PathVariable UUID id, CurrentUser me, HttpServletRequest request) {
    return LecturePlaybackResponse.from(playback.issue(me, id, request.getRemoteAddr()));
  }

  @GetMapping(value = "/hls/master.m3u8", produces = HLS) // never text/html
  ResponseEntity<String> master(
      @PathVariable UUID id, @RequestParam String token, HttpServletRequest request) {
    return playlist(playback.master(id, token, request.getRemoteAddr()));
  }

  @GetMapping(value = "/hls/{rung:[0-9]{2,4}p}/index.m3u8", produces = HLS)
  ResponseEntity<String> rung(
      @PathVariable UUID id,
      @PathVariable String rung,
      @RequestParam String token,
      HttpServletRequest request) {
    return playlist(playback.rung(id, rung, token, request.getRemoteAddr()));
  }

  /** Personal (the urls inside are this viewer's): private, briefly cacheable. */
  private static ResponseEntity<String> playlist(String body) {
    return ResponseEntity.ok()
        .contentType(HLS_TYPE)
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
        .body(body);
  }
}
