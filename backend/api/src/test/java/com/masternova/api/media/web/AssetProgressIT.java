package com.masternova.api.media.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.catalog.domain.TestMedia;
import com.masternova.storage.MediaKeys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * ⭐ The progress stream over a REAL server and a real access token — SSE is an async request, and
 * only a real servlet container re-dispatches it through the security filters the way production
 * does. The worker's side is played by publishing to Redis exactly what {@code ProgressPublisher}
 * publishes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AssetProgressIT {

  @LocalServerPort int port;
  @Autowired JwtEncoder jwt;
  @Autowired StringRedisTemplate redis;
  @Autowired DataSource dataSource;
  @Autowired AssetEvents events;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("assetEventsListener")
  org.springframework.data.redis.listener.RedisMessageListenerContainer listener;

  JdbcClient db;
  Instructor asha;
  final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() throws InterruptedException {
    for (int i = 0; i < 100 && !listener.isRunning(); i++) {
      Thread.sleep(50); // subscribed in the background after startup
    }
    db = JdbcClient.create(dataSource);
    clean();
    asha = new TestInstructors(dataSource).create("Asha Rao");
  }

  @AfterEach
  void clean() {
    JdbcClient d = JdbcClient.create(dataSource);
    d.sql("DELETE FROM pipeline_job").update();
    d.sql("DELETE FROM media_asset").update();
    new TestInstructors(dataSource).deleteAll();
  }

  private String token(UUID user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("masternova")
            .subject(user.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("roles", List.of("INSTRUCTOR"))
            .claim("email_verified", true)
            .claim("sid", UUID.randomUUID().toString())
            .build();
    return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private UUID processingAsset(String status) {
    UUID asset = new TestMedia(dataSource).asset(asha.id(), status, null);
    db.sql(
            """
            INSERT INTO pipeline_job (id, asset_id, type, rung, status, attempts, next_attempt_at,
                                      progress, created_at, updated_at)
            VALUES (gen_random_uuid(), :asset, 'TRANSCODE', '720p', 'RUNNING', 1, now(), 40, now(), now())
            """)
        .param("asset", asset)
        .update();
    return asset;
  }

  /** Opens the stream; every SSE line lands in the returned queue; the future ends with it. */
  private CompletableFuture<HttpResponse<Void>> stream(
      UUID asset, UUID as, BlockingQueue<String> lines) {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/api/v1/media/assets/" + asset + "/stream"))
            .header("Authorization", "Bearer " + token(as))
            .header("Accept", "text/event-stream")
            .build();
    return http.sendAsync(
        request, HttpResponse.BodyHandlers.fromLineSubscriber(new LineSink(lines)));
  }

  /** Collects SSE "data:" lines with the event name in front, e.g. {@code snapshot {...}}. */
  static final class LineSink implements java.util.concurrent.Flow.Subscriber<String> {
    private final BlockingQueue<String> out;
    private String event = "message";

    LineSink(BlockingQueue<String> out) {
      this.out = out;
    }

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
      s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(String line) {
      if (line.startsWith("event:")) {
        event = line.substring(6).strip();
      } else if (line.startsWith("data:")) {
        out.add(event + " " + line.substring(5).strip());
      }
    }

    @Override
    public void onError(Throwable t) {}

    @Override
    public void onComplete() {}
  }

  private static String next(BlockingQueue<String> lines) throws InterruptedException {
    String line = lines.poll(10, TimeUnit.SECONDS);
    assertThat(line).as("an SSE event within 10 s").isNotNull();
    return line;
  }

  private void awaitSubscribed() throws InterruptedException {
    for (int i = 0; i < 100 && events.openStreams() == 0; i++) {
      Thread.sleep(50);
    }
  }

  @Test
  void snapshotThenLiveTicksThenTheTerminalSnapshotClosesTheStream() throws Exception {
    UUID asset = processingAsset("PROCESSING");
    BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    var response = stream(asset, asha.id(), lines);

    String first = next(lines);
    assertThat(first)
        .startsWith("snapshot ")
        .contains("\"status\":\"PROCESSING\"", "\"percent\":40");
    awaitSubscribed();

    // the worker's tick (ProgressPublisher's exact message)
    redis.convertAndSend(
        MediaKeys.progressChannel(asset),
        "{\"kind\":\"progress\",\"assetId\":\""
            + asset
            + "\",\"job\":\"TRANSCODE\",\"rung\":\"720p\",\"percent\":75}");
    assertThat(next(lines)).startsWith("progress ").contains("\"percent\":75");

    // the job finished and the asset became READY: "changed" → a fresh snapshot from the DB
    db.sql("UPDATE pipeline_job SET status = 'DONE' WHERE asset_id = :a")
        .param("a", asset)
        .update();
    db.sql("UPDATE media_asset SET status = 'READY' WHERE id = :a").param("a", asset).update();
    redis.convertAndSend(
        MediaKeys.progressChannel(asset), "{\"kind\":\"changed\",\"assetId\":\"" + asset + "\"}");

    assertThat(next(lines))
        .startsWith("snapshot ")
        .contains("\"status\":\"READY\"", "\"percent\":100");
    HttpResponse<Void> ended = response.get(10, TimeUnit.SECONDS); // ⭐ the server closed it
    assertThat(ended.statusCode()).isEqualTo(200);
    assertThat(ended.headers().firstValue("Content-Type"))
        .hasValueSatisfying(t -> assertThat(t).startsWith("text/event-stream"));
    assertThat(ended.headers().firstValue("X-Accel-Buffering")).contains("no");
    assertThat(events.openStreams()).isZero();
  }

  @Test
  void anAlreadyFinishedAssetGetsOneSnapshotAndTheEnd() throws Exception {
    UUID asset = processingAsset("FAILED");
    BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    HttpResponse<Void> response = stream(asset, asha.id(), lines).get(10, TimeUnit.SECONDS);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(new ArrayList<>(lines)).singleElement().asString().contains("\"status\":\"FAILED\"");
  }

  @Test
  void someoneElsesAssetIsNotFound() throws Exception {
    UUID asset = processingAsset("PROCESSING");
    UUID ravi = new TestInstructors(dataSource).create("Ravi Kumar").id();

    HttpResponse<Void> response =
        stream(asset, ravi, new LinkedBlockingQueue<>()).get(10, TimeUnit.SECONDS);

    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void theOneShotSnapshotIsThereForClientsThatPoll() throws Exception {
    UUID asset = processingAsset("PROCESSING");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/v1/media/assets/" + asset))
                .header("Authorization", "Bearer " + token(asha.id()))
                .timeout(Duration.ofSeconds(10))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"jobs\":[{\"type\":\"TRANSCODE\",\"rung\":\"720p\"");
  }
}
