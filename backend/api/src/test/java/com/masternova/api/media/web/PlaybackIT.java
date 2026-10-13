package com.masternova.api.media.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.identity.Role;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Playback end to end (ADR-0013): a ticket behind the bearer, then the player's own requests — no
 * token — for the master, a rung, and a segment straight from storage (SeaweedFS).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaybackIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired ObjectStorage storage;
  JdbcClient db;
  Instructor asha;
  Instructor ravi;
  UUID asset;

  @BeforeEach
  void setUp() {
    db = JdbcClient.create(dataSource);
    clean();
    TestInstructors people = new TestInstructors(dataSource);
    asha = people.create("Asha Rao");
    ravi = people.create("Ravi Kumar");
    asset = asset("READY");
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

  private UUID asset(String status) {
    UUID id = UUID.randomUUID();
    db.sql(
            """
            INSERT INTO media_asset (id, owner_id, kind, status, original_filename, content_type,
                                     size_bytes, created_at, version)
            VALUES (:id, :owner, 'VIDEO', :status, 'v.mp4', 'video/mp4', 1, now(), 0)
            """)
        .param("id", id)
        .param("owner", asha.id())
        .param("status", status)
        .update();
    return id;
  }

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private MvcTestResult ticket(UUID assetId, UUID who) {
    return mvc.post()
        .uri("/api/v1/media/assets/{id}/playback", assetId)
        .with(as(who, Role.INSTRUCTOR))
        .exchange();
  }

  private String masterUrl() throws Exception {
    MvcTestResult granted = ticket(asset, asha.id());
    assertThat(granted).hasStatusOk();
    return JsonPath.read(granted.getResponse().getContentAsString(), "$.masterUrl");
  }

  private static String body(MvcTestResult r) throws Exception {
    return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void theOwnerGetsATicketAndThePlayerFollowsItToTheBytes() throws Exception {
    String masterUrl = masterUrl();
    String ticket = masterUrl.substring(masterUrl.indexOf("?t=") + 3);

    // the player's requests carry NO bearer token
    MvcTestResult master = mvc.get().uri(masterUrl).exchange();
    assertThat(master).hasStatusOk().hasContentTypeCompatibleWith("application/vnd.apple.mpegurl");
    assertThat(body(master)).contains("240p/index.m3u8?t=" + ticket);

    MvcTestResult rung =
        mvc.get()
            .uri("/api/v1/media/assets/{id}/hls/240p/index.m3u8?t=" + ticket, asset)
            .exchange();
    assertThat(rung).hasStatusOk();
    String segmentUrl =
        body(rung)
            .lines()
            .filter(l -> l.startsWith("http") && l.contains("seg_00000"))
            .findFirst()
            .orElseThrow();
    assertThat(body(rung)).contains("#EXT-X-MAP:URI=\"http");

    HttpResponse<String> segment =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(segmentUrl)).build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(segment.statusCode()).isEqualTo(200); // ⭐ straight from storage, presigned
    assertThat(segment.body()).isEqualTo("segment-bytes");
  }

  @Test
  void aTamperedTicketOrOneForAnotherAssetOpensNothing() throws Exception {
    String masterUrl = masterUrl();
    String ticket = masterUrl.substring(masterUrl.indexOf("?t=") + 3);

    assertThat(mvc.get().uri(masterUrl + "x")).hasStatus(HttpStatus.FORBIDDEN);
    UUID other = asset("READY");
    assertThat(mvc.get().uri("/api/v1/media/assets/{id}/hls/master.m3u8?t=" + ticket, other))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/v1/media/assets/{id}/hls/master.m3u8", asset))
        .hasStatus(HttpStatus.BAD_REQUEST); // no ticket at all
  }

  @Test
  void noTicketForSomeoneElsesOrAnUnfinishedVideo() {
    assertThat(ticket(asset, ravi.id())).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(ticket(asset("PROCESSING"), asha.id()))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("MEDIA_NOT_READY");
  }
}
