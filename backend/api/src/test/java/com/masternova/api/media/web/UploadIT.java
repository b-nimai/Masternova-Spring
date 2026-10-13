package com.masternova.api.media.web;

import static com.masternova.api.media.domain.PartPlan.MIB;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The resumable upload end to end (media LLD §5, ADR-0014): the api over MockMvc, the browser's
 * part PUTs as plain HTTP to the presigned urls, real SeaweedFS and Postgres underneath.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UploadIT {

  private static final HttpClient BROWSER = HttpClient.newHttpClient();

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired ObjectStorage storage;

  JdbcClient jdbc;
  Instructor asha;
  Instructor ravi;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    clean();
    TestInstructors instructors = new TestInstructors(dataSource);
    asha = instructors.create("Asha Rao");
    ravi = instructors.create("Ravi Kumar");
  }

  @AfterEach
  void clean() {
    JdbcClient.create(dataSource)
        .sql("DELETE FROM outbox_message WHERE event_type LIKE 'media.%'")
        .update();
    JdbcClient.create(dataSource).sql("DELETE FROM upload_session").update();
    JdbcClient.create(dataSource).sql("DELETE FROM media_asset").update();
    new TestInstructors(dataSource).deleteAll();
  }

  // ------------------------------------------------------------------ the uploader's calls

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private MvcTestResult create(UUID who, String type, long size) {
    return mvc.post()
        .uri("/api/v1/media/uploads")
        .with(as(who, Role.INSTRUCTOR))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"filename": "lecture-01.mp4", "contentType": "%s", "sizeBytes": %d}
            """
                .formatted(type, size))
        .exchange();
  }

  private MvcTestResult get(UUID who, String uploadId) {
    return mvc.get()
        .uri("/api/v1/media/uploads/{id}", uploadId)
        .with(as(who, Role.INSTRUCTOR))
        .exchange();
  }

  private MvcTestResult complete(UUID who, String uploadId) {
    return mvc.post()
        .uri("/api/v1/media/uploads/{id}/complete", uploadId)
        .with(as(who, Role.INSTRUCTOR))
        .exchange();
  }

  private MvcTestResult abort(UUID who, String uploadId) {
    return mvc.delete()
        .uri("/api/v1/media/uploads/{id}", uploadId)
        .with(as(who, Role.INSTRUCTOR))
        .exchange();
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** {part number → presigned url} from an UploadResponse. */
  private static Map<Integer, URI> urls(MvcTestResult result) {
    List<Map<String, Object>> parts = JsonPath.read(body(result), "$.parts");
    var urls = new java.util.TreeMap<Integer, URI>();
    parts.forEach(p -> urls.put((Integer) p.get("number"), URI.create((String) p.get("url"))));
    return urls;
  }

  /** The browser: a bare PUT of the part's bytes, no auth header, no SDK. */
  private static void put(URI url, long size) throws Exception {
    byte[] bytes = new byte[Math.toIntExact(size)];
    new Random(size).nextBytes(bytes);
    HttpResponse<Void> response =
        BROWSER.send(
            HttpRequest.newBuilder(url).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private int mediaUploadedEvents() {
    return jdbc.sql("SELECT count(*) FROM outbox_message WHERE event_type = 'media.uploaded.v1'")
        .query(Integer.class)
        .single();
  }

  private String assetStatus(String assetId) {
    return jdbc.sql("SELECT status FROM media_asset WHERE id = :id")
        .param("id", UUID.fromString(assetId))
        .query(String.class)
        .single();
  }

  // ------------------------------------------------------------------ tests

  @Test
  void interruptedUploadResumesFromWhatTheProviderHoldsThenCompletesOnce() throws Exception {
    MvcTestResult created = create(asha.id(), "video/mp4", 12 * MIB); // 5 + 5 + 2 MiB
    assertThat(created).hasStatus(HttpStatus.CREATED);
    String uploadId = JsonPath.read(body(created), "$.uploadId");
    String assetId = JsonPath.read(body(created), "$.assetId");
    Map<Integer, URI> urls = urls(created);
    assertThat(urls).containsOnlyKeys(1, 2, 3);

    put(urls.get(1), 5 * MIB);
    put(urls.get(3), 2 * MIB);
    // "the tab closes" — the server was never told which parts went up

    MvcTestResult resumed = get(asha.id(), uploadId);
    assertThat(resumed).hasStatusOk();
    assertThat(urls(resumed)).containsOnlyKeys(2); // ⭐ asked of the provider (ADR-0014)
    assertThat(resumed).bodyJson().extractingPath("$.uploadedBytes").isEqualTo(7 * 1024 * 1024);

    put(urls(resumed).get(2), 5 * MIB);
    MvcTestResult done = complete(asha.id(), uploadId);

    assertThat(done).hasStatusOk();
    assertThat(done).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
    assertThat(assetStatus(assetId)).isEqualTo("UPLOADED");
    assertThat(storage.head(MediaKeys.source(UUID.fromString(assetId))))
        .hasValueSatisfying(o -> assertThat(o.size()).isEqualTo(12 * MIB));
    assertThat(mediaUploadedEvents()).isEqualTo(1);

    // a retried complete REPLAYS the answer: same 200, no second event
    assertThat(complete(asha.id(), uploadId)).hasStatusOk();
    assertThat(mediaUploadedEvents()).isEqualTo(1);
    // and there's nothing left to resume
    assertThat(get(asha.id(), uploadId))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UPLOAD_ALREADY_COMPLETED");
  }

  @Test
  void completeWithAMissingPartSaysWhichOne() throws Exception {
    MvcTestResult created = create(asha.id(), "video/mp4", 12 * MIB);
    String uploadId = JsonPath.read(body(created), "$.uploadId");
    Map<Integer, URI> urls = urls(created);
    put(urls.get(1), 5 * MIB);
    put(urls.get(3), 2 * MIB);

    MvcTestResult result = complete(asha.id(), uploadId);

    assertThat(result)
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UPLOAD_INCOMPLETE");
    assertThat(result).bodyJson().extractingPath("$.missing").asArray().containsExactly(2);
    assertThat(mediaUploadedEvents()).isZero();
  }

  @Test
  void tenConcurrentCompletesStartThePipelineOnce() throws Exception {
    MvcTestResult created = create(asha.id(), "video/mp4", 6 * MIB);
    String uploadId = JsonPath.read(body(created), "$.uploadId");
    Map<Integer, URI> urls = urls(created);
    put(urls.get(1), 5 * MIB);
    put(urls.get(2), MIB);

    int clicks = 10;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(clicks)) {
      for (int i = 0; i < clicks; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return complete(asha.id(), uploadId).getResponse().getStatus();
                }));
      }
      start.countDown();
      for (Future<Integer> status : results) {
        assertThat(status.get()).isEqualTo(200); // ⭐ the losers wait on the row lock, then REPLAY
      }
    }
    assertThat(mediaUploadedEvents()).isEqualTo(1);
  }

  @Test
  void theProviderCompletedButOurCommitDidNotTheRetryHeals() throws Exception {
    MvcTestResult created = create(asha.id(), "video/mp4", 3 * MIB);
    String uploadId = JsonPath.read(body(created), "$.uploadId");
    String assetId = JsonPath.read(body(created), "$.assetId");
    put(urls(created).get(1), 3 * MIB);
    // simulate "S3's complete succeeded, then the api died before committing"
    String key = MediaKeys.source(UUID.fromString(assetId));
    String providerUploadId =
        jdbc.sql("SELECT provider_upload_id FROM upload_session WHERE id = :id")
            .param("id", UUID.fromString(uploadId))
            .query(String.class)
            .single();
    storage.completeMultipartUpload(
        key, providerUploadId, storage.listParts(key, providerUploadId));

    MvcTestResult retry = complete(asha.id(), uploadId); // ListParts → NoSuchUpload → HEAD

    assertThat(retry).hasStatusOk();
    assertThat(assetStatus(assetId)).isEqualTo("UPLOADED");
    assertThat(mediaUploadedEvents()).isEqualTo(1);
  }

  @Test
  void abortIsIdempotentAndEndsTheUpload() {
    MvcTestResult created = create(asha.id(), "video/mp4", MIB);
    String uploadId = JsonPath.read(body(created), "$.uploadId");
    String assetId = JsonPath.read(body(created), "$.assetId");

    assertThat(abort(asha.id(), uploadId)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(abort(asha.id(), uploadId)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(complete(asha.id(), uploadId))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UPLOAD_ABORTED");
    assertThat(storage.listMultipartUploads(MediaKeys.asset(UUID.fromString(assetId)))).isEmpty();
    assertThat(assetStatus(assetId)).isEqualTo("FAILED");
  }

  @Test
  void someoneElsesUploadIsNotFound() {
    String uploadId = JsonPath.read(body(create(asha.id(), "video/mp4", MIB)), "$.uploadId");

    assertThat(get(ravi.id(), uploadId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(complete(ravi.id(), uploadId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(abort(ravi.id(), uploadId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void theFileIsCheckedBeforeAnythingIsCreated() {
    assertThat(create(asha.id(), "application/pdf", MIB))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    assertThat(create(asha.id(), "video/mp4", 10L * 1024 * MIB + 1))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UPLOAD_TOO_LARGE");
    assertThat(jdbc.sql("SELECT count(*) FROM media_asset").query(Integer.class).single()).isZero();
  }

  @Test
  void learnersCannotUpload() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/media/uploads")
            .with(as(asha.id(), Role.LEARNER))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"filename\": \"a.mp4\", \"contentType\": \"video/mp4\", \"sizeBytes\": 10}")
            .exchange();
    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
  }
}
