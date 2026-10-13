package com.masternova.storage;

/**
 * Our failure contract for storage, independent of the SDK. The base type is PERMANENT (a retry
 * can't help: access denied, a bad request); {@link StorageUnavailableException} is temporary and
 * {@link NoSuchUploadException} has its own recovery (media LLD §5).
 */
public class StorageException extends RuntimeException {

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
