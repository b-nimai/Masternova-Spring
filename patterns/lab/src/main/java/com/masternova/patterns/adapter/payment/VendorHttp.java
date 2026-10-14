package com.masternova.patterns.adapter.payment;

import java.util.Map;

/**
 * The ADAPTEE: a vendor's HTTP API as it really is — maps in, maps out, status codes for failures.
 * A test scripts its answers.
 */
public interface VendorHttp {

  /** Thrown for any non-2xx answer. */
  final class HttpError extends RuntimeException {
    public final int status;

    public HttpError(int status, String body) {
      super(status + " " + body);
      this.status = status;
    }
  }

  /** Thrown when nothing answered (timeout, refused connection). */
  final class NoAnswer extends RuntimeException {
    public NoAnswer(String message) {
      super(message);
    }
  }

  Map<String, Object> post(String path, Map<String, Object> body);
}
