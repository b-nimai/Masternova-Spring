package com.masternova.patterns.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlobStoreTest {

  /** A vendor that fails every call the same way. */
  static VendorBlobSdk failingWith(RuntimeException error) {
    return new VendorBlobSdk() {
      @Override
      public long contentLength(String bucket, String key) {
        throw error;
      }

      @Override
      public void abortUpload(String bucket, String key, String uploadId) {
        throw error;
      }
    };
  }

  @Test
  void notFoundIsAnAnswerNotAFailure() {
    var store = new BlobStore(failingWith(new VendorBlobSdk.ServiceError(404, "NoSuchKey")), "b");
    assertThat(store.size("k")).isEmpty();
    assertThatCode(() -> store.abort("k", "u")).doesNotThrowAnyException();
  }

  @Test
  void serverErrorsThrottlingAndSilenceAreTemporary() {
    for (RuntimeException vendor :
        new RuntimeException[] {
          new VendorBlobSdk.ServiceError(503, "SlowDown"),
          new VendorBlobSdk.ServiceError(429, "TooManyRequests"),
          new VendorBlobSdk.ClientError("connection refused")
        }) {
      assertThatThrownBy(() -> new BlobStore(failingWith(vendor), "b").size("k"))
          .isInstanceOf(BlobStore.Unavailable.class)
          .hasCause(vendor); // ⭐ the vendor's exception is kept as the cause, for the logs
    }
  }

  @Test
  void clientErrorsArePermanent() {
    var store = new BlobStore(failingWith(new VendorBlobSdk.ServiceError(403, "AccessDenied")), "b");
    assertThatThrownBy(() -> store.size("k")).isExactlyInstanceOf(BlobStore.Failed.class);
  }

  @Test
  void aSuccessfulCallIsJustMapped() {
    var store =
        new BlobStore(
            new VendorBlobSdk() {
              @Override
              public long contentLength(String bucket, String key) {
                return 42;
              }

              @Override
              public void abortUpload(String bucket, String key, String uploadId) {}
            },
            "b");
    assertThat(store.size("k")).contains(42L);
  }
}
