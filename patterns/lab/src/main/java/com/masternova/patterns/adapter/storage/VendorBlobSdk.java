package com.masternova.patterns.adapter.storage;

/**
 * ADAPTEE: a stand-in for a cloud SDK (the real one is the AWS SDK's {@code S3Client}). Its
 * failures are ITS types — an HTTP status plus an error-code string — and a separate exception for
 * "no answer at all".
 */
public interface VendorBlobSdk {

  /** The vendor's "the server answered with an error". */
  final class ServiceError extends RuntimeException {
    public final int status;
    public final String code;

    public ServiceError(int status, String code) {
      super(status + " " + code);
      this.status = status;
      this.code = code;
    }
  }

  /** The vendor's "the request never got an answer" (connection refused, timeout). */
  final class ClientError extends RuntimeException {
    public ClientError(String message) {
      super(message);
    }
  }

  long contentLength(String bucket, String key);

  void abortUpload(String bucket, String key, String uploadId);
}
