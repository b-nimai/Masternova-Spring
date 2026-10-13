package com.masternova.storage;

/**
 * Temporary: the provider is down, slow or throttling. Callers answer 503 or retry with backoff.
 */
public class StorageUnavailableException extends StorageException {

  public StorageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
