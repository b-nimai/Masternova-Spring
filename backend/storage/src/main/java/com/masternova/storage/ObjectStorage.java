package com.masternova.storage;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsResponse;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * ADAPTER: what media and the pipeline need from "a bucket", over the AWS SDK v2 ({@link S3Client}
 * + {@link S3Presigner}, the Adaptees). Nothing outside this class sees an SDK type: calls take
 * keys and return {@link StoredPart} / {@link StoredObject}, and SDK failures are translated into
 * OUR contract — {@link StorageUnavailableException} (temporary), {@link NoSuchUploadException},
 * {@link StorageException} (permanent) — the same permanent/temporary split as {@code
 * MailProvider}.
 *
 * <p>⭐ A concrete class, no interface: there is ONE implementation (S3's API, served by S3 and by
 * SeaweedFS in dev), and the ITs run that real API in a container — there is no fake to swap in
 * (CLAUDE.md §0: one implementation is not a seam).
 *
 * <p>Two presigners on purpose: {@code browser} signs urls with the host the BROWSER reaches
 * ({@code masternova.storage.public-endpoint}); {@code internal} signs with the host the servers
 * reach (the worker's ffmpeg). SigV4 signs the host header, so one url can't serve both.
 */
@DesignPattern(value = Pattern.ADAPTER, role = "Adapter", note = "patterns/docs/12-adapter.md")
public class ObjectStorage implements AutoCloseable {

  private final S3Client s3;
  private final S3Presigner browser;
  private final S3Presigner internal;
  private final String bucket;

  public ObjectStorage(S3Client s3, S3Presigner browser, S3Presigner internal, String bucket) {
    this.s3 = s3;
    this.browser = browser;
    this.internal = internal;
    this.bucket = bucket;
  }

  public String bucket() {
    return bucket;
  }

  /** The presigners hold credential providers; the {@link S3Client} is closed by its own bean. */
  @Override
  public void close() {
    browser.close();
    internal.close();
  }

  // ---- multipart uploads (browser → storage, media LLD §5) ----

  /**
   * @return the provider's upload id
   */
  public String createMultipartUpload(String key, String contentType) {
    return call(
        "create multipart " + key,
        () ->
            s3.createMultipartUpload(b -> b.bucket(bucket).key(key).contentType(contentType))
                .uploadId());
  }

  /** A url the browser {@code PUT}s one part's bytes to, with no other headers needed. */
  public URI presignUploadPart(String key, String uploadId, int partNumber, Duration ttl) {
    return call(
        "presign part",
        () ->
            toUri(
                browser
                    .presignUploadPart(
                        p ->
                            p.signatureDuration(ttl)
                                .uploadPartRequest(
                                    r ->
                                        r.bucket(bucket)
                                            .key(key)
                                            .uploadId(uploadId)
                                            .partNumber(partNumber)))
                    .url()
                    .toString()));
  }

  /** ⭐ The provider's truth about an upload (ADR-0014), every page, ordered by part number. */
  public List<StoredPart> listParts(String key, String uploadId) {
    return call(
        "list parts " + key,
        () -> {
          List<StoredPart> parts = new ArrayList<>();
          Integer marker = null;
          ListPartsResponse page;
          do {
            Integer from = marker;
            page =
                s3.listParts(
                    b -> b.bucket(bucket).key(key).uploadId(uploadId).partNumberMarker(from));
            page.parts()
                .forEach(p -> parts.add(new StoredPart(p.partNumber(), p.size(), p.eTag())));
            marker = page.nextPartNumberMarker();
          } while (Boolean.TRUE.equals(page.isTruncated()));
          return List.copyOf(parts);
        },
        uploadId);
  }

  /** Completes with the PROVIDER's part list — callers pass what {@link #listParts} returned. */
  public void completeMultipartUpload(String key, String uploadId, List<StoredPart> parts) {
    List<CompletedPart> completed =
        parts.stream()
            .map(p -> CompletedPart.builder().partNumber(p.number()).eTag(p.etag()).build())
            .toList();
    run(
        "complete " + key,
        () ->
            s3.completeMultipartUpload(
                b ->
                    b.bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(m -> m.parts(completed))),
        uploadId);
  }

  /** Idempotent: aborting an upload the provider already forgot is success. */
  public void abortMultipartUpload(String key, String uploadId) {
    try {
      run(
          "abort " + key,
          () -> s3.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId)),
          uploadId);
    } catch (NoSuchUploadException alreadyGone) {
      // the goal state — "this upload doesn't exist" — already holds
    }
  }

  /** Open uploads under {@code prefix}, every page — the orphan sweeper's input (7.6). */
  public List<PendingUpload> listMultipartUploads(String prefix) {
    return call(
        "list uploads " + prefix,
        () -> {
          List<PendingUpload> uploads = new ArrayList<>();
          String keyMarker = null;
          String idMarker = null;
          ListMultipartUploadsResponse page;
          do {
            String k = keyMarker;
            String i = idMarker;
            page =
                s3.listMultipartUploads(
                    b -> b.bucket(bucket).prefix(prefix).keyMarker(k).uploadIdMarker(i));
            page.uploads()
                .forEach(u -> uploads.add(new PendingUpload(u.key(), u.uploadId(), u.initiated())));
            keyMarker = page.nextKeyMarker();
            idMarker = page.nextUploadIdMarker();
          } while (Boolean.TRUE.equals(page.isTruncated()));
          return List.copyOf(uploads);
        });
  }

  // ---- objects ----

  public Optional<StoredObject> head(String key) {
    try {
      HeadObjectResponse head =
          call("head " + key, () -> s3.headObject(b -> b.bucket(bucket).key(key)));
      return Optional.of(new StoredObject(key, head.contentLength(), head.contentType()));
    } catch (StorageException e) {
      if (e.getCause() instanceof NoSuchKeyException
          || (e.getCause() instanceof S3Exception s3e && s3e.statusCode() == 404)) {
        return Optional.empty(); // HEAD has no body, so a 404 arrives without the NoSuchKey code
      }
      throw e;
    }
  }

  /** Uploads a local file. Same key twice = overwrite: that's how a rerun stays idempotent. */
  public void put(String key, Path file, String contentType) {
    run(
        "put " + key,
        () -> s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), file));
  }

  public void putText(String key, String content, String contentType) {
    run(
        "put " + key,
        () ->
            s3.putObject(
                b -> b.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromString(content, StandardCharsets.UTF_8)));
  }

  public String getText(String key) {
    return call(
        "get " + key,
        () -> s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asString(StandardCharsets.UTF_8));
  }

  /** Every key under {@code prefix}, in key order. */
  public List<String> listKeys(String prefix) {
    return call(
        "list " + prefix,
        () ->
            s3.listObjectsV2Paginator(b -> b.bucket(bucket).prefix(prefix)).contents().stream()
                .map(S3Object::key)
                .toList());
  }

  /** A GET url for the browser (segments, posters). */
  public URI presignDownload(String key, Duration ttl) {
    return presignGet(browser, key, ttl);
  }

  /** A GET url for our own servers — ffmpeg reads a source by url with range seeks (7.5). */
  public URI presignInternalDownload(String key, Duration ttl) {
    return presignGet(internal, key, ttl);
  }

  private URI presignGet(S3Presigner presigner, String key, Duration ttl) {
    return call(
        "presign get",
        () ->
            toUri(
                presigner
                    .presignGetObject(
                        p ->
                            p.signatureDuration(ttl)
                                .getObjectRequest(r -> r.bucket(bucket).key(key)))
                    .url()
                    .toString()));
  }

  // ---- ⭐ the adapting: SDK failures → our contract ----

  private <T> T call(String what, Supplier<T> action) {
    return call(what, action, null);
  }

  private <T> T call(String what, Supplier<T> action, String uploadId) {
    try {
      return action.get();
    } catch (RuntimeException e) {
      throw translate(what, e, uploadId);
    }
  }

  private void run(String what, Runnable action) {
    run(what, action, null);
  }

  private void run(String what, Runnable action, String uploadId) {
    call(
        what,
        () -> {
          action.run();
          return null;
        },
        uploadId);
  }

  static StorageException translate(String what, RuntimeException e, String uploadId) {
    if (e instanceof StorageException ours) {
      return ours;
    }
    if (e instanceof S3Exception s3e) {
      String code = s3e.awsErrorDetails() == null ? null : s3e.awsErrorDetails().errorCode();
      if ("NoSuchUpload".equals(code) && uploadId != null) {
        return new NoSuchUploadException(uploadId, e);
      }
      if (s3e.statusCode() >= 500 || s3e.statusCode() == 429 || "SlowDown".equals(code)) {
        return new StorageUnavailableException(what + ": " + s3e.statusCode() + " " + code, e);
      }
      return new StorageException(what + ": " + s3e.statusCode() + " " + code, e);
    }
    if (e instanceof SdkClientException) {
      // connection refused, timeouts, DNS: the request never got an answer
      return new StorageUnavailableException(what + ": " + e.getMessage(), e);
    }
    return new StorageException(what + ": " + e.getMessage(), e);
  }

  private static URI toUri(String url) {
    return URI.create(url);
  }
}
