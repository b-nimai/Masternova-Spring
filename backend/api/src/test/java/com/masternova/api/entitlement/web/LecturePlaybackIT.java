package com.masternova.api.entitlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.catalog.domain.TestMedia;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
import com.masternova.api.identity.Role;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Learner playback end to end (ADR-0017): the entitlement decision behind {@code @PreAuthorize}, a
 * token bound to the learner's IP, then the player's own requests — no bearer — for the master, a
 * rung and a segment straight from storage (SeaweedFS).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LecturePlaybackIT {

  static final String HOME = "203.0.113.7";
  static final String ELSEWHERE = "198.51.100.9";

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired ObjectStorage storage;
  @Autowired EntitlementApi entitlements;
  UUID instructor;
  UUID learner;
  UUID asset;
  Seeded course;

  @BeforeEach
  void setUp() {
    TestInstructors people = new TestInstructors(dataSource);
    instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    asset = new TestMedia(dataSource).asset(instructor, "READY", 60);
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, asset);
    // what the worker's pipeline leaves in storage
    storage.putText(
        MediaKeys.master(asset),
        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=385000,RESOLUTION=426x240\n240p/index.m3u8\n",
        "application/vnd.apple.mpegurl");
    storage.putText(
        MediaKeys.rungPlaylist(asset, "240p"),
        "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:6.0,\nseg_00000.m4s\n#EXT-X-ENDLIST\n",
        "application/vnd.apple.mpegurl");
    storage.putText(MediaKeys.segment(asset, "240p", 0), "segment-bytes", "video/iso.segment");
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  private static RequestPostProcessor as(UUID id) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of("LEARNER")))
        .authorities(
            List.<GrantedAuthority>of(new SimpleGrantedAuthority(Role.LEARNER.authority())));
  }

  private static RequestPostProcessor from(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  private MvcTestResult issue(UUID lectureId) {
    return mvc.post()
        .uri("/api/v1/lectures/{id}/playback", lectureId)
        .with(as(learner))
        .with(from(HOME))
        .exchange();
  }

  private String manifestUrl(UUID lectureId) throws Exception {
    MvcTestResult issued = issue(lectureId);
    assertThat(issued).hasStatusOk();
    return JsonPath.read(issued.getResponse().getContentAsString(), "$.manifestUrl");
  }

  private static String body(MvcTestResult r) throws Exception {
    return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private void buy() {
    entitlements.grant(learner, course.courseId(), EntitlementSource.PURCHASE, Optional.empty());
  }

  @Test
  void aBuyerGetsAShortTokenAndThePlayerFollowsItToTheBytes() throws Exception {
    buy();
    MvcTestResult issued = issue(course.paidLecture());
    assertThat(issued).hasStatusOk();
    Instant expiresAt =
        Instant.parse(JsonPath.read(issued.getResponse().getContentAsString(), "$.expiresAt"));
    assertThat(expiresAt).isBefore(Instant.now().plus(Duration.ofMinutes(5).plusSeconds(5)));
    String manifestUrl = JsonPath.read(issued.getResponse().getContentAsString(), "$.manifestUrl");
    String token = manifestUrl.substring(manifestUrl.indexOf("?token=") + 7);

    // the player's requests carry NO bearer token
    MvcTestResult master = mvc.get().uri(manifestUrl).with(from(HOME)).exchange();
    assertThat(master).hasStatusOk().hasContentTypeCompatibleWith("application/vnd.apple.mpegurl");
    assertThat(body(master)).contains("240p/index.m3u8?token=" + token);

    MvcTestResult rung =
        mvc.get()
            .uri("/api/v1/lectures/{id}/hls/240p/index.m3u8?token=" + token, course.paidLecture())
            .with(from(HOME))
            .exchange();
    assertThat(rung).hasStatusOk();
    String segmentUrl =
        body(rung).lines().filter(l -> l.startsWith("http")).findFirst().orElseThrow();
    HttpResponse<String> segment =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(segmentUrl)).build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(segment.statusCode()).isEqualTo(200); // ⭐ straight from storage, presigned
    assertThat(segment.body()).isEqualTo("segment-bytes");
  }

  @Test
  void theLinkIsWorthlessFromAnotherNetworkOrForAnotherLecture() throws Exception {
    buy();
    String manifestUrl = manifestUrl(course.paidLecture());
    String token = manifestUrl.substring(manifestUrl.indexOf("?token=") + 7);

    assertThat(mvc.get().uri(manifestUrl).with(from(ELSEWHERE)))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("PLAYBACK_TOKEN_INVALID");
    assertThat(
            mvc.get()
                .uri(
                    "/api/v1/lectures/{id}/hls/master.m3u8?token=" + token, course.previewLecture())
                .with(from(HOME)))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void withoutTheCourseTheDenialSaysWhy() {
    assertThat(issue(course.paidLecture()))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("NO_ENTITLEMENT"); // ⭐ from the chain, through @PreAuthorize, into the 403
    assertThat(issue(course.previewLecture())).hasStatusOk(); // previews need no purchase
  }

  @Test
  void aRefundStopsTheNextTicket() {
    buy();
    assertThat(issue(course.paidLecture())).hasStatusOk();

    entitlements.revoke(learner, course.courseId(), "refund");

    assertThat(issue(course.paidLecture()))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("ENTITLEMENT_REVOKED");
  }

  @Test
  void onlyAFinishedVideoPlays() {
    UUID processing = new TestMedia(dataSource).asset(instructor, "PROCESSING", null);
    Seeded unfinished =
        new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, processing);
    Seeded textOnly = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null);

    assertThat(issue(unfinished.paidLecture()))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("MEDIA_NOT_READY");
    assertThat(issue(textOnly.paidLecture()))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("LECTURE_HAS_NO_VIDEO");
  }

  @Test
  void unknownLecturesAndAnonymousCallers() {
    assertThat(issue(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.post().uri("/api/v1/lectures/{id}/playback", course.previewLecture()))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.get()
                .uri("/api/v1/lectures/{id}/hls/master.m3u8", course.paidLecture())
                .with(from(HOME)))
        .hasStatus(HttpStatus.BAD_REQUEST); // no token at all
  }
}
