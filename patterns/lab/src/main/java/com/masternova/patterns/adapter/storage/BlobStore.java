package com.masternova.patterns.adapter.storage;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * ⭐ An ADAPTER with no Target interface: there is one vendor API and no second one planned, so
 * the "interface our code speaks" is simply this class's public methods. What still makes it an
 * adapter is the TRANSLATION — keys in, our types out, and vendor failures turned into OUR
 * contract:
 *
 * <ul>
 *   <li>{@link Unavailable}: temporary (5xx, 429, no answer) → retry / 503;
 *   <li>{@link Missing}: "not there" → usually not an error at all ({@code Optional.empty()}, or an
 *       idempotent no-op);
 *   <li>{@link Failed}: permanent (403, 400).
 * </ul>
 *
 * Mirrors {@code com.masternova.storage.ObjectStorage}.
 */
public final class BlobStore {

  public static class Failed extends RuntimeException {
    Failed(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class Unavailable extends Failed {
    Unavailable(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class Missing extends Failed {
    Missing(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private final VendorBlobSdk sdk;
  private final String bucket;

  public BlobStore(VendorBlobSdk sdk, String bucket) {
    this.sdk = sdk;
    this.bucket = bucket;
  }

  /** "Not there" is an answer, not a failure. */
  public Optional<Long> size(String key) {
    try {
      return Optional.of(call(() -> sdk.contentLength(bucket, key)));
    } catch (Missing e) {
      return Optional.empty();
    }
  }

  /** Idempotent: the goal state ("no such upload") may already hold. */
  public void abort(String key, String uploadId) {
    try {
      call(
          () -> {
            sdk.abortUpload(bucket, key, uploadId);
            return null;
          });
    } catch (Missing alreadyGone) {
      // fine
    }
  }

  private static <T> T call(Supplier<T> action) {
    try {
      return action.get();
    } catch (VendorBlobSdk.ServiceError e) {
      if (e.status == 404) {
        throw new Missing(e.getMessage(), e);
      }
      if (e.status >= 500 || e.status == 429) {
        throw new Unavailable(e.getMessage(), e);
      }
      throw new Failed(e.getMessage(), e);
    } catch (VendorBlobSdk.ClientError e) {
      throw new Unavailable(e.getMessage(), e);
    }
  }
}
