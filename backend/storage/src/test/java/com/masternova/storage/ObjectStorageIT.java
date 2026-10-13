package com.masternova.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The real S3 API (SeaweedFS, ADR-0012) through the auto-configured {@link ObjectStorage}: the
 * browser's half of an upload is a plain {@link HttpClient} PUT to a presigned url — no SDK, no
 * extra headers — exactly what the Angular uploader will send (7.8).
 */
@Testcontainers
class ObjectStorageIT {

  @Container static final SeaweedFsContainer SEAWEED = new SeaweedFsContainer();

  private static final int MIB = 1024 * 1024;
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static S3Client s3;
  private static ObjectStorage storage;

  @BeforeAll
  static void start() {
    // the same factory methods Boot calls, so the IT exercises the real wiring (checksums, path
    // style, both presigners)
    var config = new StorageAutoConfiguration();
    var props = SEAWEED.storageProperties();
    s3 = config.s3Client(props);
    storage = config.objectStorage(s3, props);
  }

  @AfterAll
  static void stop() {
    storage.close();
    s3.close();
  }

  static int put(URI url, byte[] body) throws Exception {
    return HTTP.send(
            HttpRequest.newBuilder(url).PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  @Test
  void multipartUploadThroughPresignedUrlsResumesFromTheProvidersPartList() throws Exception {
    String key = MediaKeys.source(UUID.randomUUID());
    byte[] part1 = random(5 * MIB);
    byte[] part2 = random(1234);
    String uploadId = storage.createMultipartUpload(key, "video/mp4");

    // the browser uploads part 2 only, then "the tab closes"
    assertThat(put(storage.presignUploadPart(key, uploadId, 2, Duration.ofMinutes(5)), part2))
        .isEqualTo(200);

    // ⭐ resume = ask the provider (ADR-0014): part 1 is the one missing
    assertThat(storage.listParts(key, uploadId)).extracting(StoredPart::number).containsExactly(2);

    assertThat(put(storage.presignUploadPart(key, uploadId, 1, Duration.ofMinutes(5)), part1))
        .isEqualTo(200);
    List<StoredPart> parts = storage.listParts(key, uploadId);
    assertThat(parts).extracting(StoredPart::size).containsExactly((long) part1.length, 1234L);

    storage.completeMultipartUpload(key, uploadId, parts); // the provider's ETags, not the client's

    assertThat(storage.head(key))
        .hasValueSatisfying(o -> assertThat(o.size()).isEqualTo(part1.length + part2.length));
    // the upload id is gone once completed — the media LLD §5 recovery path starts from this
    assertThatThrownBy(() -> storage.listParts(key, uploadId))
        .isInstanceOf(NoSuchUploadException.class);
  }

  @Test
  void aTamperedPresignedUrlIsRejected() throws Exception {
    String key = MediaKeys.source(UUID.randomUUID());
    String uploadId = storage.createMultipartUpload(key, "video/mp4");
    String url = storage.presignUploadPart(key, uploadId, 1, Duration.ofMinutes(5)).toString();

    URI otherPart = URI.create(url.replace("partNumber=1", "partNumber=2"));
    assertThat(put(otherPart, random(10))).isEqualTo(403);
    assertThat(storage.listParts(key, uploadId)).isEmpty();
  }

  @Test
  void abortIsIdempotentAndTheSweeperSeesOnlyOpenUploads() {
    String prefix = "media/" + UUID.randomUUID() + "/";
    String open = storage.createMultipartUpload(prefix + "source", "video/mp4");
    String aborted = storage.createMultipartUpload(prefix + "other", "video/mp4");

    storage.abortMultipartUpload(prefix + "other", aborted);
    storage.abortMultipartUpload(prefix + "other", aborted); // again: still fine

    assertThat(storage.listMultipartUploads(prefix))
        .extracting(PendingUpload::uploadId)
        .containsExactly(open);
  }

  @Test
  void sameKeyTwiceOverwritesAndRangedPresignedGetsWork(@TempDir Path dir) throws Exception {
    UUID asset = UUID.randomUUID();
    String key = MediaKeys.segment(asset, "360p", 0);
    Path file = Files.write(dir.resolve("seg.m4s"), random(4096));

    storage.put(key, file, "video/iso.segment");
    storage.put(key, file, "video/iso.segment"); // a rerun (7.10)
    storage.putText(
        MediaKeys.rungPlaylist(asset, "360p"), "#EXTM3U\n", "application/vnd.apple.mpegurl");

    assertThat(storage.listKeys(MediaKeys.hls(asset)))
        .containsExactly(MediaKeys.rungPlaylist(asset, "360p"), key);
    assertThat(storage.getText(MediaKeys.rungPlaylist(asset, "360p"))).isEqualTo("#EXTM3U\n");

    HttpResponse<byte[]> ranged =
        HTTP.send(
            HttpRequest.newBuilder(storage.presignDownload(key, Duration.ofMinutes(1)))
                .header("Range", "bytes=0-99")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(ranged.statusCode()).isEqualTo(206);
    assertThat(ranged.body()).isEqualTo(Arrays.copyOf(Files.readAllBytes(file), 100));
  }

  @Test
  void headOfAMissingKeyIsEmpty() {
    assertThat(storage.head("media/" + UUID.randomUUID() + "/source")).isEmpty();
  }

  private static byte[] random(int size) {
    byte[] bytes = new byte[size];
    new Random(size).nextBytes(bytes);
    return bytes;
  }
}
